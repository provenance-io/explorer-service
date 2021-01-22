ALTER USER postgres PASSWORD 'password1';
CREATE DATABASE explorer;
GRANT ALL ON DATABASE explorer TO postgres;
CREATE SCHEMA IF NOT EXISTS explorer AUTHORIZATION postgres;
GRANT ALL ON SCHEMA explorer TO postgres;
