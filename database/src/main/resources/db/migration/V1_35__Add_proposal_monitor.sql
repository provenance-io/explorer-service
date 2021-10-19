SELECT 'Add proposal_monitor' AS comment;
CREATE TABLE IF NOT EXISTS proposal_monitor
(
    id                         SERIAL PRIMARY KEY,
    proposal_id                INT          NOT NULL,
    submitted_height           INT          NOT NULL,
    proposed_completion_height INT          NOT NULL,
    voting_end_time             TIMESTAMP   NOT NULL,
    proposal_type              VARCHAR(256) NOT NULL,
    matching_data_hash         VARCHAR(256) NOT NULL,
    ready_for_processing       BOOLEAN DEFAULT FALSE,
    processed                  BOOLEAN DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS proposal_monitor_proposal_id_idx ON proposal_monitor(proposal_id);
