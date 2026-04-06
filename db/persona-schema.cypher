// Constraints and indexes for Persona nodes.
// Run against an existing database to add constraints.
// Safe to re-run — uses IF NOT EXISTS.

CREATE CONSTRAINT persona_id_unique IF NOT EXISTS
FOR (p:Persona) REQUIRE p.id IS UNIQUE;

CREATE INDEX persona_name_idx IF NOT EXISTS
FOR (p:Persona) ON (p.name);