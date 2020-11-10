ALTER USER postgres PASSWORD 'password1';
CREATE DATABASE provenanceexplorer;
GRANT ALL ON DATABASE provenanceexplorer TO postgres;
CREATE SCHEMA IF NOT EXISTS provenanceexplorer AUTHORIZATION postgres;
GRANT ALL ON SCHEMA provenanceexplorer TO postgres;