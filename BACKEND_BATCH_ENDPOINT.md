# Endpoint de Batch para Eventos - Backend

## Implementação Sugerida para o Backend

Adicione este endpoint ao arquivo `adm_calm_wave/back-end/app/routes/events.py`:

```python
@events_bp.route("/batch", methods=["POST"])
@jwt_required()
def create_events_batch():
    """
    Cria múltiplos eventos de uma só vez (batch)
    
    Request:
    {
        "events": [
            {
                "user_id": 1,
                "event_type": "AUDIO_PROCESSED",
                "details": { ... },
                "screen": "GravarActivity",
                "level": "info"
            },
            ...
        ]
    }
    
    Response:
    {
        "created": 10,
        "failed": 0,
        "errors": []
    }
    """
    data = request.get_json()
    events_data = data.get("events", [])
    
    if not events_data or not isinstance(events_data, list):
        return jsonify({"error": "events array is required"}), 400
    
    import json
    created_count = 0
    failed_count = 0
    errors = []
    
    for idx, event_data in enumerate(events_data):
        try:
            event = Event(
                user_id=event_data.get("user_id"),
                event_type=event_data.get("event_type", "unknown"),
                details_json=json.dumps(event_data.get("details", {})),
                screen=event_data.get("screen"),
                level=event_data.get("level", "info"),
            )
            db.session.add(event)
            created_count += 1
        except Exception as e:
            failed_count += 1
            errors.append({
                "index": idx,
                "error": str(e),
                "event_type": event_data.get("event_type", "unknown")
            })
    
    try:
        db.session.commit()
    except Exception as e:
        db.session.rollback()
        return jsonify({
            "error": "Database commit failed",
            "message": str(e)
        }), 500
    
    return jsonify({
        "created": created_count,
        "failed": failed_count,
        "errors": errors
    }), 201 if failed_count == 0 else 207  # 207 = Multi-Status
```

## Como Adicionar

1. Abra o arquivo `back-end/app/routes/events.py`
2. Adicione a função `create_events_batch()` após a função `create_event()`
3. Reinicie o servidor Flask

## Testando

```bash
curl -X POST http://localhost:5000/api/events/batch \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "events": [
      {
        "user_id": 1,
        "event_type": "AUDIO_PROCESSED",
        "details": {"duration_ms": 5000},
        "screen": "GravarActivity",
        "level": "info"
      },
      {
        "user_id": 1,
        "event_type": "AUDIO_PLAYED",
        "details": {"filename": "test.wav"},
        "screen": "PrincipalActivity",
        "level": "info"
      }
    ]
  }'
```

## Benefícios

- **Performance**: Reduz número de requisições HTTP
- **Eficiência**: Menos overhead de rede
- **Atomicidade**: Todos os eventos são criados em uma transação
- **Economia de Bateria**: Menos conexões de rede no mobile

## Alternativa: JWT Opcional

Se quiser permitir eventos anônimos (sem autenticação), use `@jwt_required(optional=True)`:

```python
from flask_jwt_extended import jwt_required, get_jwt_identity

@events_bp.route("/batch", methods=["POST"])
@jwt_required(optional=True)
def create_events_batch():
    current_user_id = get_jwt_identity()  # None se não autenticado
    # ... resto do código
```
