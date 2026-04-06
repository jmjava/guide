// Unique constraints for WebUser
// Run against an existing database to add constraints.
// Safe to re-run — uses IF NOT EXISTS.

CREATE CONSTRAINT webuser_username_unique IF NOT EXISTS
FOR (w:WebUser) REQUIRE w.userName IS UNIQUE;

CREATE CONSTRAINT webuser_email_unique IF NOT EXISTS
FOR (w:WebUser) REQUIRE w.userEmail IS UNIQUE;
