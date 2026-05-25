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

Traces worden op dit moment nergens persistent opgeslagen — ze verschijnen alleen in de containerlogs.

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

## Gevolgen

### Positief

- Services zijn ontkoppeld van de monitoring-backend — overstappen van Prometheus naar een ander systeem vereist alleen een aanpassing van de OTel Collector-configuratie, niet van de services zelf.
- De wachtrijdiepte van RabbitMQ is zichtbaar, waardoor een opgehoopte notificatiewachtrij snel opvalt.
- Het `service_name`-label op elke metric maakt het mogelijk om per service te filteren in Grafana.

### Negatief / huidige beperkingen

- Traces worden alleen naar stdout geschreven (debug-exporter). Er is geen trace-opslag (bijv. Jaeger of Tempo), waardoor distributed tracing achteraf niet kan worden opgevraagd.
- De notifworker en scheduler genereren nog geen aangepaste metrics (bijv. verzonden notificaties per provider, aantal herhaalpogingen). Het dashboard toont alleen API-niveau- en RabbitMQ-metrics.
- Metrics worden slechts bewaard zolang Prometheus ze in de lokale opslag houdt (standaard 15 dagen). Er is geen langetermijnopslag geconfigureerd.
