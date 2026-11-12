CREATE TABLE job_descriptions (
    job_description_id UUID PRIMARY KEY,
    description TEXT NOT NULL,
    required_skills TEXT NOT NULL
);

CREATE TABLE cv_documents (
    cv_document_id UUID PRIMARY KEY,
    file_name TEXT,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    sha256 VARCHAR(64) NOT NULL CHECK (char_length(sha256) = 64)
);

CREATE TABLE analyses (
    analysis_id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    mode VARCHAR(16) NOT NULL CHECK (mode IN ('tfidf', 'ai')),
    job_description_id UUID NOT NULL UNIQUE REFERENCES job_descriptions(job_description_id)
);

CREATE INDEX idx_analyses_created_at ON analyses (created_at DESC, analysis_id DESC);

CREATE TABLE matching_results (
    matching_result_id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analyses(analysis_id) ON DELETE CASCADE,
    cv_document_id UUID NOT NULL UNIQUE REFERENCES cv_documents(cv_document_id),
    candidate_rank INTEGER NOT NULL CHECK (candidate_rank >= 0),
    technical_score DOUBLE PRECISION NOT NULL,
    similarity DOUBLE PRECISION NOT NULL,
    matched_count INTEGER NOT NULL CHECK (matched_count >= 0),
    required_count INTEGER NOT NULL CHECK (required_count >= 0),
    CONSTRAINT uk_matching_result_rank UNIQUE (analysis_id, candidate_rank)
);

CREATE TABLE matching_skills (
    matching_skill_id UUID PRIMARY KEY,
    matching_result_id UUID NOT NULL REFERENCES matching_results(matching_result_id) ON DELETE CASCADE,
    skill_name VARCHAR(255) NOT NULL,
    match_status VARCHAR(16) NOT NULL CHECK (match_status IN ('MATCHED', 'MISSING')),
    skill_order INTEGER NOT NULL CHECK (skill_order >= 0),
    CONSTRAINT uk_matching_skill_order UNIQUE (matching_result_id, skill_order)
);
