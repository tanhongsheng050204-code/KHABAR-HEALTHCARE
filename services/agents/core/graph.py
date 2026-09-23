"""
The patient graph (Neo4j), read-only. Spring Boot writes it; this service only reads, in a
read-access session, with queries that cannot change anything. The graph knows each patient only by
a random graph ID: no name, IC number or phone number is ever in it.

Without NEO4J_URI there is no graph, and every agent works from what Spring Boot sends it instead.
"""
import logging
from typing import Any, Optional

import neo4j

from core.config import settings

log = logging.getLogger(__name__)

PATIENT = "MATCH (p:Patient {graph_id: $graph_id}) RETURN p.pregnant AS pregnant"
CONDITIONS = ("MATCH (:Patient {graph_id: $graph_id})-[:HAS_CONDITION]->(c:Condition) "
              "RETURN c.name AS name ORDER BY name")
ALLERGIES = ("MATCH (:Patient {graph_id: $graph_id})-[:ALLERGIC_TO]->(a:Allergy) "
             "RETURN a.name AS name ORDER BY name")
MEDICINES = ("MATCH (:Patient {graph_id: $graph_id})-[t:TAKES]->(m:Medication) "
             "RETURN t.name AS name, m.generic AS generic, m.recognised AS recognised, t.source AS source "
             "ORDER BY t.since, name")
HERBS = ("MATCH (:Patient {graph_id: $graph_id})-[u:USES]->(h:Herb) "
         "RETURN u.name AS name, h.name AS herb, u.source AS source ORDER BY u.since, name")
LAST_VISIT = ("MATCH (:Patient {graph_id: $graph_id})-[:HAD]->(e:Encounter) "
              "WITH e ORDER BY e.at DESC LIMIT 1 "
              "OPTIONAL MATCH (e)-[:PRESCRIBED]->(m:Medication) "
              "RETURN toString(e.at) AS at, collect(m.generic) AS prescribed")
SYMPTOMS = ("MATCH (:Patient {graph_id: $graph_id})-[r:REPORTED]->(s:Symptom) "
            "RETURN s.word AS word, r.level AS level, toString(r.at) AS at ORDER BY r.at DESC LIMIT 10")
READINGS = ("MATCH (:Patient {graph_id: $graph_id})-[:RECORDED]->(r:Reading) "
            "RETURN r.kind AS kind, r.value AS value, r.level AS level, toString(r.at) AS at ORDER BY r.at DESC LIMIT 10")

READ_QUERIES = (PATIENT, CONDITIONS, ALLERGIES, MEDICINES, HERBS, LAST_VISIT, SYMPTOMS, READINGS)


def _rows(result) -> list[dict[str, Any]]:
    return [row if isinstance(row, dict) else row.data() for row in result]


class PatientGraphReader:
    def __init__(self, driver):
        self.driver = driver

    def context(self, graph_id: str) -> Optional[dict[str, Any]]:
        """What the graph knows about one patient, or None if the graph has never seen them."""
        with self.driver.session(default_access_mode=neo4j.READ_ACCESS) as session:
            return session.execute_read(lambda tx: self._context(tx, graph_id))

    @staticmethod
    def _context(tx, graph_id: str) -> Optional[dict[str, Any]]:
        def run(query):
            return _rows(tx.run(query, graph_id=graph_id))

        patient = run(PATIENT)
        if not patient:
            return None
        visit = run(LAST_VISIT)
        return {
            "pregnant": bool(patient[0].get("pregnant")),
            "conditions": [r["name"] for r in run(CONDITIONS)],
            "allergies": [r["name"] for r in run(ALLERGIES)],
            "medicines": [{"name": r["name"], "generic": r["generic"] if r.get("recognised") else None, "source": r["source"]}
                          for r in run(MEDICINES)],
            "herbs": [{"name": r["name"], "herb": r["herb"], "source": r["source"]} for r in run(HERBS)],
            "last_visit": visit[0] if visit and visit[0].get("at") else None,
            "recent_symptoms": run(SYMPTOMS),
            "recent_readings": run(READINGS),
        }


_reader: Optional[PatientGraphReader] = None


def reader() -> Optional[PatientGraphReader]:
    """The shared reader, or None when no graph is configured."""
    global _reader
    if not settings.NEO4J_URI:
        return None
    if _reader is None:
        driver = neo4j.GraphDatabase.driver(settings.NEO4J_URI, auth=(settings.NEO4J_USERNAME, settings.NEO4J_PASSWORD))
        _reader = PatientGraphReader(driver)
    return _reader


def reset_reader() -> None:
    global _reader
    _reader = None


def context_or_none(graph_id: Optional[str]) -> Optional[dict[str, Any]]:
    """For the agents' tools: the graph context, or None when there is no graph, no patient, or it is unreachable."""
    graph = reader()
    if graph is None or not graph_id:
        return None
    try:
        return graph.context(graph_id)
    except Exception as e:  # the graph adds context; an agent never fails because it is down
        log.warning("Patient graph unavailable: %s", e)
        return None
