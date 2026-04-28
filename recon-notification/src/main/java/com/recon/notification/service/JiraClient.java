package com.recon.notification.service;

import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface JiraClient {

    @PostExchange("/rest/api/2/issue")
    void createIssue(JiraIssueRequest request);

    record JiraIssueRequest(Fields fields) {
        public record Fields(Project project, String summary, String description, IssueType issuetype) {
        }

        public record Project(String key) {
        }

        public record IssueType(String name) {
        }
    }
}

