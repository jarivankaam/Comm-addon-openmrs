# ADR-8: Monitoring Stack

## Status

**Status:** Geaccepteerd

**Datum:** 23 mei 2026

**Besluitvormer:** Architectuurteam

## Context

Het systeem bestaat uit meerdere services (api, scheduler, notifworker) die observeerbaar moeten zijn in productie. We hebben inzicht nodig in HTTP-verkeer, de gezondheid van message queues, notificatie-aflevering en JVM-prestaties zonder een sterke koppeling te creëren tussen de services en een specifieke monitoring-backend.

## Besluit

We gebruiken een drielaagse monitoring-stack: **OpenTelemetry → Prometheus → Grafana**.

### Architectuur

```
Services (api, scheduler, notifworker)
    │
    │  OTLP/gRPC (poort 4317)
    ▼
OTel Collector
    │                  │
    │ Prometheus scrape │ debug (stdout)
    │ endpoint :8889    │ traces
    ▼
Prometheus (:9090)        scrapet ook:
    │                     RabbitMQ plugin (:15692)
    ▼
Grafana (:3000)
```

### Hoe elke laag werkt

**Services → OTel Collector**

Elke service stelt de volgende omgevingsvariabelen in, die worden opgepikt door de OpenTelemetry Java-agent:

| Variabele | Waarde |
|---|---|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://otel-collector:4317` |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` |
| `OTEL_METRICS_EXPORTER` | `otlp` |
| `OTEL_TRACES_EXPORTER` | `otlp` |
| `OTEL_LOGS_EXPORTER` | `none` |
| `OTEL_SERVICE_NAME` | bijv. `azaricomm-api` |

De agent instrumenteert Spring Boot automatisch — zonder codewijzigingen worden de volgende metrics gegenereerd en via OTLP naar de collector gestuurd:

**1. Auto-geïnstrumenteerde HTTP-metrics**

| Metric | Type | Beschrijving |
|---|---|---|
| `http_server_request_duration_seconds` | Histogram | Duur van inkomende HTTP-verzoeken. Levert request rate, latency-distributies (p50/p95/p99) en foutpercentages op. |

Beschikbare labels: `http_request_method`, `http_response_status_code`, `url_path`, `service_name`.

**2. Auto-geïnstrumenteerde JVM-metrics**

| Metric | Type | Beschrijving |
|---|---|---|
| `jvm_memory_used_bytes` | Gauge | Hoeveelheid JVM-geheugen in gebruik, opgesplitst per geheugentype (heap / non-heap). |
| `jvm_gc_duration_seconds` | Summary | Tijd besteed aan garbage collection pauzes. |
| `jvm_threads_live_threads` | Gauge | Aantal actieve threads in de JVM. |
| `jvm_classes_loaded_classes` | Gauge | Aantal geladen classes. |

**3. Aangepaste business-metrics (alleen api-service)**

| Metric | Type | Beschrijving |
|---|---|---|
| `appointments_received_total` | Counter | Totaal aantal ontvangen afspraken. |
| `appointments_cancelled_total` | Counter | Totaal aantal geannuleerde afspraken. |

Deze twee counters zijn handmatig gedefinieerd via Micrometer met het OTLP-register. De notifworker en scheduler hebben op dit moment nog **geen** eigen custom metrics — zie [Huidige beperkingen](#negatief--huidige-beperkingen).

**OTel Collector**

Ontvangt OTLP via gRPC op poort `4317`. Er zijn twee pipelines geconfigureerd:

- **Metrics-pipeline**: batch → exporteer naar een Prometheus scrape-endpoint op `:8889`
- **Traces-pipeline**: batch → exporteer naar stdout (`debug`-exporter)

Traces worden op dit moment nergens persistent opgeslagen — ze verschijnen alleen in de containerlogs. Zie [Distributed tracing](#distributed-tracing) voor de huidige status en het beoogde groeipad.

**Prometheus**

Scrapet elke 15 seconden twee targets:

| Job | Target | Wat het oplevert |
|---|---|---|
| `otel-collector` | `otel-collector:8889` | HTTP-, JVM- en aangepaste business-metrics van alle services (zie hierboven) |
| `rabbitmq` | `rabbitmq:15692` | Wachtrijdiepte, berichtsnelheden via de `rabbitmq_prometheus`-plugin |

> **Let op:** RabbitMQ-metrics lopen *niet* via de OTel Collector. Prometheus scrapet deze rechtstreeks van de RabbitMQ-container via de ingebouwde `rabbitmq_prometheus`-plugin. Dit is een aparte datastroom ten opzichte van de servicemetrics.

**Grafana**

Leest uit Prometheus als enige databron. Het `azaricomm-monitoring`-dashboard wordt automatisch ingericht vanuit `config/monitoring/grafana/dashboards/azaricomm-monitoring.json` en bevat de volgende panelen:

| Paneel | Type | Query |
|---|---|---|
| HTTP-verzoeksnelheid (req/s) | Tijdreeks | `sum(rate(http_server_request_duration_seconds_count[1m]))` |
| HTTP-verzoekduur p95 | Tijdreeks | `histogram_quantile(0.95, sum(rate(http_server_request_duration_seconds_bucket[5m])) by (le))` |
| HTTP-verzoeken per statuscode | Tijdreeks | `sum(rate(..._count[1m])) by (http_response_status_code)` |
| Afspraken ontvangen | Statistiek | `sum(appointments_received_total)` |
| Afspraken geannuleerd | Statistiek | `sum(appointments_cancelled_total)` |
| JVM-geheugengebruik | Tijdreeks | `sum(jvm_memory_used_bytes{service_name="azaricomm-api"}) by (jvm_memory_type)` |
| JVM GC-pauzeduur | Tijdreeks | `rate(jvm_gc_duration_seconds_sum{service_name="azaricomm-api"}[5m])` |
| RabbitMQ-wachtrijberichten | Tijdreeks | `rabbitmq_queue_messages` |
| RabbitMQ-berichtpublicatiesnelheid | Tijdreeks | `rate(rabbitmq_queue_messages_published_total[1m])` |

### Distributed tracing

De OTel Java-agent genereert automatisch trace spans voor inkomende HTTP-verzoeken, uitgaande HTTP-calls en JDBC-queries. Dit betekent dat de instrumentatie al aanwezig is in alle drie de services — er is geen applicatiecode voor nodig.

**Huidige situatie**

Traces worden via OTLP naar de OTel Collector gestuurd en daar geëxporteerd via de `debug`-exporter, wat betekent dat ze als JSON in de containerlogs van de collector verschijnen. Dit is bruikbaar voor ad-hoc debugging (`docker logs otel-collector | grep traceId`), maar biedt geen doorzoekbare opslag of visualisatie.

**Belangrijk: trace context propagation over RabbitMQ**

HTTP-verkeer propageert de W3C `traceparent`-header automatisch, maar message brokers doen dit niet standaard. Om een trace te kunnen volgen van de api-service door RabbitMQ heen naar de notifworker, is ondersteuning nodig voor het injecteren en extraheren van trace context in AMQP-berichtproperties. De OTel Java-agent biedt hiervoor auto-instrumentatie via de `spring-rabbit`-module, maar dit is nog niet expliciet geverifieerd in onze setup. Zonder deze propagatie verschijnen de api-service en de notifworker als losse traces in plaats van als één samenhangende keten.

**Beoogd groeipad**

Wanneer er behoefte is aan persistent opvraagbare traces (bijv. voor het analyseren van langzame afspraakverwerking of het debuggen van gefaalde notificaties), kan de traces-pipeline worden uitgebreid door de `debug`-exporter te vervangen (of aan te vullen) met een OTLP-exporter richting een trace-backend zoals Jaeger of Grafana Tempo. Dit vereist:

1. Een Jaeger- of Tempo-container toevoegen aan de Docker Compose-stack.
2. De OTel Collector-configuratie aanpassen: in de `exporters`-sectie een `otlp/jaeger`- of `otlp/tempo`-exporter toevoegen en deze koppelen aan de traces-pipeline.
3. In Grafana een tweede databron configureren die naar de trace-backend wijst.

Er zijn geen wijzigingen aan de services zelf nodig — het voordeel van de ontkoppeling via de OTel Collector.

### Queue depth monitoring

RabbitMQ-wachtrijdiepte is een van de belangrijkste operationele signalen in deze architectuur. Wanneer de notifworker uitvalt, traag verwerkt, of een fout herhaaldelijk optreedt, stapelen berichten zich op in de queue. De metric `rabbitmq_queue_messages` is daarmee het vroegste signaal dat er iets mis is aan de consumerkant.

**Beschikbare metrics**

| Metric | Beschrijving | Signaal |
|---|---|---|
| `rabbitmq_queue_messages` | Totaal aantal berichten in de wachtrij (ready + unacked). | Een stijgende trend wijst op een oplopende achterstand — de consumer kan de productie niet bijhouden of is gestopt. |
| `rabbitmq_queue_messages_ready` | Berichten die klaarstaan om door een consumer opgehaald te worden. | Hoge waarde bij lage `unacked`: de consumer haalt berichten niet op (mogelijk offline of niet verbonden). |
| `rabbitmq_queue_messages_unacknowledged` | Berichten die door een consumer zijn opgehaald maar nog niet bevestigd (acknowledged). | Hoge waarde wijst erop dat de consumer berichten ontvangt maar niet succesvol verwerkt — bijv. door exceptions, timeouts of een vastgelopen verwerkingsproces. |
| `rabbitmq_queue_messages_published_total` | Totaal aantal gepubliceerde berichten (counter). | Bruikbaar als rate (`rate(...[1m])`) om de instroom te vergelijken met de verwerkingssnelheid. |

**Operationele interpretatie**

Het onderscheid tussen `messages_ready` en `messages_unacknowledged` is belangrijk voor troubleshooting:

- **Hoge `ready`, lage `unacked`**: de consumer draait niet of is niet verbonden met de queue. Controleer of de notifworker-container actief is en verbinding heeft met RabbitMQ.
- **Lage `ready`, hoge `unacked`**: de consumer haalt berichten op maar kan ze niet verwerken. Controleer de logs van de notifworker op exceptions of timeouts.
- **Beide hoog**: de consumer draait maar kan de instroom niet bijhouden. Overweeg horizontaal schalen van de notifworker of het verhogen van de prefetch count.

**Aanbeveling: alerting**

Het dashboard toont wachtrijdiepte als tijdreeks, maar biedt geen actieve waarschuwing. Een logische volgende stap is het toevoegen van een Prometheus alerting rule, bijvoorbeeld:

```yaml
groups:
  - name: rabbitmq
    rules:
      - alert: QueueBacklogHigh
        expr: rabbitmq_queue_messages > 100
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "RabbitMQ-wachtrij heeft meer dan 100 berichten voor langer dan 5 minuten"
```

De drempelwaarde (`100`) en duur (`5m`) moeten worden afgestemd op het verwachte berichtvolume en de verwerkingssnelheid van de notifworker.

## Gevolgen

### Positief

- Services zijn ontkoppeld van de monitoring-backend — overstappen van Prometheus naar een ander systeem vereist alleen een aanpassing van de OTel Collector-configuratie, niet van de services zelf.
- De wachtrijdiepte van RabbitMQ is zichtbaar, waardoor een opgehoopte notificatiewachtrij snel opvalt. Het onderscheid tussen `ready` en `unacked` berichten maakt het mogelijk om de oorzaak van een backlog sneller te diagnosticeren.
- Het `service_name`-label op elke metric maakt het mogelijk om per service te filteren in Grafana.
- Trace-instrumentatie is al aanwezig in alle services via de OTel Java-agent; het activeren van een trace-backend vereist alleen een configuratiewijziging in de collector.

### Negatief / huidige beperkingen

- Traces worden alleen naar stdout geschreven (debug-exporter). Er is geen trace-opslag (bijv. Jaeger of Tempo), waardoor distributed tracing achteraf niet kan worden opgevraagd. Het groeipad hiervoor is beschreven in de sectie [Distributed tracing](#distributed-tracing).
- Trace context propagation over RabbitMQ is nog niet geverifieerd. Zonder werkende propagatie zijn traces van de api-service en de notifworker niet aan elkaar te koppelen.
- De notifworker en scheduler genereren nog geen aangepaste metrics (bijv. verzonden notificaties per provider, aantal herhaalpogingen). Het dashboard toont alleen API-niveau- en RabbitMQ-metrics.
- Er zijn geen alerting rules geconfigureerd. Problemen worden pas opgemerkt wanneer iemand het dashboard bekijkt.
- Metrics worden slechts bewaard zolang Prometheus ze in de lokale opslag houdt (standaard 15 dagen). Er is geen langetermijnopslag geconfigureerd.
