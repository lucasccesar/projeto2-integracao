# Demonstração de Webhooks e Idempotência

Projeto de seminário com três containers: `payment-provider` envia webhooks HTTP reais para `order-service`, que salva pedidos e eventos processados no PostgreSQL. O contador representa um efeito de negócio didático, não uma movimentação financeira.

## Requisitos

- Windows 10/11 com Docker Desktop iniciado e Docker Compose.
- Internet disponível na primeira construção para baixar imagens e dependências. Depois disso, a demonstração roda localmente.
- Maven e Java não são necessários no computador: os serviços são compilados em imagens Docker com Java 21.

## Iniciar

No PowerShell, na pasta deste projeto:

```powershell
docker compose up --build
```

O Compose inicia exatamente `postgres`, `order-service` e `payment-provider`. Em outro PowerShell:

```powershell
docker compose ps
```

As APIs ficam em `http://localhost:8081` e `http://localhost:8082`. Para acompanhar os registros:

```powershell
docker compose logs -f payment-provider order-service
```

## Payloads e respostas

Criação do pedido:

```json
{"orderId":"order-123","amountCents":10000}
```

O provedor gera um evento neste formato e envia por HTTP à outra aplicação:

```json
{"eventId":"evt-...","eventType":"payment.approved","orderId":"order-123","amountCents":10000}
```

`POST /orders` retorna `201`; as consultas retornam `200`. O webhook responde `200` com `PROCESSED`, `ALREADY_PROCESSED` ou `ALREADY_PAID`. O provedor responde `200` com `deliveryOutcome` (`DELIVERED`, `DELIVERY_FAILED` ou `RECEIVER_REJECTED`) e, quando recebido, `receiverResult`. Campos inválidos retornam `400`, pedido/evento desconhecido `404` e IDs ou valores conflitantes `409`.

## Roteiro ao vivo

### 1. Criar pedido pendente

```powershell
$order = Invoke-RestMethod -Method Post -Uri http://localhost:8081/orders -ContentType "application/json" -Body (@{ orderId = "order-123"; amountCents = 10000 } | ConvertTo-Json)
$order | ConvertTo-Json
```

Resposta HTTP `201`: `status` deve ser `PENDING` e `paymentApplications` deve ser `0`.

```powershell
Invoke-RestMethod http://localhost:8081/orders/order-123 | ConvertTo-Json
```

### 2. Aprovar e entregar o primeiro webhook

```powershell
$approval = Invoke-RestMethod -Method Post -Uri http://localhost:8082/payments/approve -ContentType "application/json" -Body (@{ orderId = "order-123"; amountCents = 10000 } | ConvertTo-Json)
$approval | ConvertTo-Json
$eventId = $approval.eventId
```

O retorno mostra o `eventId`, `deliveryOutcome = DELIVERED` e `receiverResult = PROCESSED`. Nos logs, mostre o envio HTTP do provedor e o processamento pelo receptor. Consulte o pedido: agora deve estar `PAID`, com `paymentApplications = 1`.

```powershell
Invoke-RestMethod http://localhost:8081/orders/order-123 | ConvertTo-Json
```

### 3. Reenviar o mesmo evento duas vezes

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8082/payments/events/$eventId/redeliver" | ConvertTo-Json
Invoke-RestMethod http://localhost:8081/orders/order-123 | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8082/payments/events/$eventId/redeliver" | ConvertTo-Json
Invoke-RestMethod http://localhost:8081/orders/order-123 | ConvertTo-Json
```

Cada resposta do provedor mostra `deliveryOutcome = DELIVERED` e `receiverResult = ALREADY_PROCESSED`. O pedido continua com contador `1`.

### 4. Conferir persistência e reiniciar o receptor

```powershell
Invoke-RestMethod http://localhost:8081/webhook-events | ConvertTo-Json -Depth 4
docker compose exec postgres psql -U demo -d demo -c "SELECT order_id, status, payment_applications FROM orders;"
docker compose exec postgres psql -U demo -d demo -c "SELECT event_id, order_id, amount_cents, processed_at FROM processed_webhooks ORDER BY processed_at DESC;"
```

O ID do webhook aparece uma única vez na tabela. Reinicie apenas o receptor e aguarde voltar:

```powershell
docker compose restart order-service
do { Start-Sleep -Seconds 2; try { Invoke-RestMethod http://localhost:8081/orders/order-123 | Out-Null; $ready = $true } catch { $ready = $false } } while (-not $ready)
Invoke-RestMethod -Method Post -Uri "http://localhost:8082/payments/events/$eventId/redeliver" | ConvertTo-Json
```

O provedor mantém o evento em memória; o banco preserva a deduplicação após a reinicialização do receptor. O resultado continua `ALREADY_PROCESSED`.

### 5. Simular falha temporária e reentrega

Crie outro pedido antes de parar o receptor:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8081/orders -ContentType "application/json" -Body (@{ orderId = "order-456"; amountCents = 2500 } | ConvertTo-Json) | ConvertTo-Json
docker compose stop order-service
$failed = Invoke-RestMethod -Method Post -Uri http://localhost:8082/payments/approve -ContentType "application/json" -Body (@{ orderId = "order-456"; amountCents = 2500 } | ConvertTo-Json)
$failed | ConvertTo-Json
$failedEventId = $failed.eventId
docker compose start order-service
do { Start-Sleep -Seconds 2; try { Invoke-RestMethod http://localhost:8081/orders/order-456 | Out-Null; $ready = $true } catch { $ready = $false } } while (-not $ready)
Invoke-RestMethod -Method Post -Uri "http://localhost:8082/payments/events/$failedEventId/redeliver" | ConvertTo-Json
Invoke-RestMethod http://localhost:8081/orders/order-456 | ConvertTo-Json
```

Com o receptor parado, o provedor retorna `DELIVERY_FAILED`, mas conserva o evento e seu corpo em memória. Após a reentrega, o resultado é `PROCESSED` e o contador fica em `1`.

## Por que existe idempotência?

Uma resposta HTTP pode se perder depois que o receptor já gravou a operação. Como o provedor não sabe se o pedido foi processado, ele pode tentar novamente. O receptor registra cada `eventId` com chave única e atualiza o pedido na mesma transação: a repetição do evento não reaplica o efeito.

## Encerrar e dados

Para parar e remover os containers, preservando o banco:

```powershell
docker compose down
```

Para remover também o volume e apagar pedidos e eventos salvos, use o comando destrutivo abaixo:

```powershell
docker compose down -v
```

O volume nomeado `postgres_data` mantém os dados entre reinicializações e entre `docker compose down` e uma próxima inicialização.
