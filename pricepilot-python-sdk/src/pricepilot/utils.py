from typing import Dict, Any

def clean_params(params: Dict[str, Any]) -> Dict[str, Any]:
    """Filters out key-value pairs where value is None.
    
    Also converts boolean values to lowercase string representations
    since Java backend APIs expect 'true'/'false'.
    """
    cleaned = {}
    for k, v in params.items():
        if v is None:
            continue
        if isinstance(v, bool):
            cleaned[k] = str(v).lower()
        else:
            cleaned[k] = v
    return cleaned
