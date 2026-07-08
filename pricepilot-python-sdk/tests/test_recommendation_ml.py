import os
import sys
import pytest
import pandas as pd
import numpy as np

# Inject workspace path so pricepilot_ml can be imported
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))

from pricepilot_ml.recommendation.models.popularity import PopularityRecommender
from pricepilot_ml.recommendation.models.content_based import ContentBasedRecommender
from pricepilot_ml.recommendation.models.collaborative import CollaborativeFilteringRecommender
from pricepilot_ml.recommendation.models.hybrid import HybridRecommender
from pricepilot_ml.recommendation.explainability.explanations import RecommendationExplainer
from pricepilot_ml.recommendation.persistence.model_store import ModelStore
from pricepilot_ml.recommendation.evaluation.metrics import OfflineMetrics
from pricepilot_ml.recommendation.evaluation.offline import OfflineEvaluator
from pricepilot_ml.recommendation.engine.rule_engine import RuleBasedRecommendationEngine
from pricepilot_ml.recommendation.engine.popularity_engine import PopularityRecommendationEngine
from pricepilot_ml.recommendation.engine.content_engine import ContentBasedRecommendationEngine
from pricepilot_ml.recommendation.engine.collaborative_engine import CollaborativeFilteringEngine
from pricepilot_ml.recommendation.engine.hybrid_engine import HybridRecommendationEngine
from pricepilot_ml.recommendation.training.trainer import Trainer

@pytest.fixture
def sample_data():
    df_products = pd.DataFrame({
        "id": ["p1", "p2", "p3"],
        "category": ["Electronics", "Electronics", "Clothing"],
        "brand": ["Dell", "Sony", "Nike"],
        "currentMinPrice": [100.0, 150.0, 50.0],
        "discount_ratio": [0.10, 0.20, 0.05],
        "averageSellerRating": [4.5, 4.0, 3.8],
        "viewCount": [200.0, 50.0, 10.0],
        "saveCount": [10.0, 2.0, 0.0],
        "watchlistCount": [5.0, 0.0, 0.0],
        "trendingScore": [50.0, 10.0, 1.0]
    })
    
    df_users = pd.DataFrame({
        "userId": ["u1", "u2"],
        "preferredCategory": ["Electronics", "Clothing"],
        "preferredBrand": ["Dell", "Nike"],
        "minPriceViewed": [80.0, 30.0],
        "maxPriceViewed": [200.0, 80.0]
    })
    
    df_interactions = pd.DataFrame({
        "userId": ["u1", "u1", "u2"],
        "productId": ["p1", "p2", "p3"],
        "interactionType": ["PRODUCT_VIEW", "PRODUCT_SAVE", "PRODUCT_VIEW"]
    })
    
    return df_products, df_users, df_interactions

def test_popularity_recommender(sample_data):
    df_prod, df_user, df_int = sample_data
    recommender = PopularityRecommender()
    recommender.fit(df_prod)
    predictions = recommender.predict("u1", df_prod, limit=2)
    
    assert len(predictions) == 2
    # p1 should be ranked higher due to higher viewCount/saveCount/watchlistCount/trending
    assert predictions[0][0] == "p1"
    assert predictions[0][1] > predictions[1][1]

def test_content_recommender(sample_data):
    df_prod, df_user, df_int = sample_data
    recommender = ContentBasedRecommender()
    recommender.fit(df_prod, df_int)
    predictions = recommender.predict("u1", df_prod, limit=2)
    
    # User u1 has history of p1 and p2 (both Electronics). 
    # Therefore, user vector should match category Electronics.
    # Recommended product should be p3 (since user already interacted with p1 and p2, which are excluded in ContentBased predict)
    assert len(predictions) == 1
    assert predictions[0][0] == "p3"

def test_collaborative_recommender(sample_data):
    df_prod, df_user, df_int = sample_data
    recommender = CollaborativeFilteringRecommender()
    recommender.fit(df_int)
    predictions = recommender.predict("u1", df_prod, limit=2)
    
    # Since u1 and u2 have disjoint interactions, sim score is 0, list is empty or fallback values
    # Let's verify prediction shape
    assert isinstance(predictions, list)

def test_hybrid_recommender(sample_data):
    df_prod, df_user, df_int = sample_data
    pop = PopularityRecommender()
    pop.fit(df_prod)
    content = ContentBasedRecommender()
    content.fit(df_prod, df_int)
    collab = CollaborativeFilteringRecommender()
    collab.fit(df_int)
    
    hybrid = HybridRecommender(pop, content, collab)
    predictions = hybrid.predict("u1", df_prod, limit=2)
    
    assert len(predictions) <= 2

def test_explainability(sample_data):
    df_prod, df_user, df_int = sample_data
    explainer = RecommendationExplainer(df_user, df_prod)
    
    explanation = explainer.explain(
        user_id="u1",
        product_id="p1",
        score=0.95,
        algorithm="Hybrid"
    )
    
    assert explanation["productId"] == "p1"
    assert explanation["score"] == 0.95
    assert explanation["algorithm"] == "Hybrid"
    assert any("Matches your preferred category" in reason for reason in explanation["reasons"])

def test_model_store_persistence(tmp_path):
    store = ModelStore(base_dir=str(tmp_path))
    model = {"dummy": "weights"}
    eval_metrics = {"precisionAt10": 0.85, "recallAt10": 0.75, "coverage": 0.6}
    config = {"param1": True}
    
    meta_path = store.persist(
        model,
        algorithm="Popularity",
        dataset_version="2.0.0",
        feature_version="1.0.0",
        evaluation_metrics=eval_metrics,
        configuration=config
    )
    
    assert os.path.exists(meta_path)
    loaded_meta = store.load_metadata("Popularity")
    assert loaded_meta["algorithm"] == "Popularity"
    assert loaded_meta["datasetVersion"] == "2.0.0"
    assert loaded_meta["precisionAt10"] == 0.85

def test_offline_metrics():
    rec = ["p1", "p2", "p3"]
    gt = {"p2", "p4"}
    
    p = OfflineMetrics.precision_at_k(rec, gt, k=2)
    r = OfflineMetrics.recall_at_k(rec, gt, k=2)
    ndcg = OfflineMetrics.ndcg_at_k(rec, gt, k=2)
    
    assert p == 0.5 # p2 in gt, p1 not.
    assert r == 0.5 # 1 out of 2 in gt.
    assert ndcg > 0.0

def test_offline_evaluator(sample_data):
    df_prod, df_user, df_int = sample_data
    evaluator = OfflineEvaluator(df_products=df_prod, df_interactions=df_int, test_ratio=0.5)
    train_df, test_gt = evaluator.train_test_split()
    
    assert not train_df.empty
    assert isinstance(test_gt, dict)

def test_trainer_pipeline(tmp_path):
    trainer = Trainer(base_dir=str(tmp_path))
    report = trainer.train_and_evaluate(dataset_version="test_v1", k=2)
    
    assert "metrics" in report
    assert "trainedAt" in report
    assert "datasetVersion" in report
