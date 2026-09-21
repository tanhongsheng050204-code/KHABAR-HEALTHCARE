# Khabar — Technical Explanation & Architecture Notes

> **Daily Log:** What the code merged today does and why. (Defense for SDC Handbook §8.5.7 / §8.5.3).

---

## 22 Sep 2026: Multi-Service Scaffold & Thin Slice Wiring

### 1. Architecture Overview

We established the dual-backend architecture required to isolate clinical health records and PII from generative AI agents:

1. **`services/api` (Spring Boot 3, Java 21)**:
   - **Role**: System of record for all clinical data.
   - **Why Java/Spring Boot**: Strict type safety, robust JPA data mapping, mature RBAC, and centralized encryption. Only Spring Boot holds the AES-256 field encryption key and talks to Supabase PostgreSQL.
   - **Communication**: Interacts with the Python AI agent service over HTTP using Spring 6 `RestClient` with a shared secret header (`X-Internal-Service-Key`).

2. **`services/agents` (FastAPI, Python 3.14)**:
   - **Role**: AI orchestration engine hosting 4 LangGraph agents (Intake, Report, Evaluator, Follow-up).
   - **Why Python/FastAPI**: Native ecosystem for LangGraph, LangChain Google GenAI (Gemini), and audio/vision models.
   - **Security Guarantee**: Agents NEVER touch PostgreSQL directly and NEVER receive patient names, IC numbers, or phone numbers. All state uses de-identified `graph_id` strings and queries Neo4j for clinical Graph-RAG context.

3. **`infra/` (Docker & Environment Config)**:
   - `docker-compose.yml`: Enables running the multi-service system locally.
   - `.env.example`: Centralizes secrets for Supabase, Neo4j AuraDB, Gemini, and internal service tokens.

### 2. Endpoints Implemented in the Scaffold

- `GET /health` (`services/agents`): Public liveness and service metadata.
- `POST /agents/intake/chat` (`services/agents`): Authenticated intake chat node executed via LangGraph. Rejects calls without valid `X-Internal-Service-Key`.
- `GET /api/health` (`services/api`): Clinical API health status and target agent service verification.
- `POST /api/intake/chat` (`services/api`): Clinical proxy endpoint bridging client intake sessions to the AI agent service.
