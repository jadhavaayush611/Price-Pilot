import os
import sys
import argparse
import json

# Ensure parent directory is in Python path to import pricepilot_ml correctly
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from pricepilot_ml.recommendation.training.trainer import Trainer

def main():
    parser = argparse.ArgumentParser(description="PricePilot ML Recommendation Engine Training Pipeline")
    parser.add_argument("--dataset-version", type=str, default="1.0.0", help="Version of the dataset to load/save")
    parser.add_argument("--k", type=int, default=10, help="K for precision@k, recall@k evaluation")
    parser.add_argument("--base-dir", type=str, default=".", help="Base directory of the project")
    
    args = parser.parse_args()
    
    print("=" * 60)
    print("Starting PricePilot Recommendation Model Training Pipeline")
    print(f"Dataset Version: {args.dataset_version}")
    print(f"Evaluation K:    {args.k}")
    print(f"Base Directory:  {args.base_dir}")
    print("=" * 60)
    
    # Correct base dir if executed from within pricepilot_ml directory
    base_dir = args.base_dir
    if os.path.basename(os.path.abspath(base_dir)) == "pricepilot_ml":
        base_dir = os.path.abspath(os.path.join(base_dir, ".."))
        
    trainer = Trainer(base_dir=base_dir)
    
    try:
        report = trainer.train_and_evaluate(dataset_version=args.dataset_version, k=args.k)
        print("\nTraining completed successfully!")
        print(f"Report saved. Performance metrics (Precision@{args.k}):")
        for algo, algo_metrics in report["metrics"].items():
            prec = algo_metrics.get("precisionAtK", 0.0)
            rec = algo_metrics.get("recallAtK", 0.0)
            cov = algo_metrics.get("coverage", 0.0)
            div = algo_metrics.get("diversity", 0.0)
            print(f"  - {algo.capitalize()}: Precision={prec:.4f}, Recall={rec:.4f}, Coverage={cov:.4f}, Diversity={div:.4f}")
        print("=" * 60)
    except Exception as e:
        print(f"\nTraining pipeline failed: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
