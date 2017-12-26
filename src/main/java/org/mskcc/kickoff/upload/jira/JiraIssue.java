package org.mskcc.kickoff.upload.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssue {
    private Fields fields;

    public JiraIssue() {
    }

    public Fields getFields() {
        return fields;
    }

    public void setFields(Fields fields) {
        this.fields = fields;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fields {
        @JsonProperty("attachment")
        private List<Attachment> attachments;

        public List<Attachment> getAttachments() {
            return attachments;
        }

        public void setAttachments(List<Attachment> attachments) {
            this.attachments = attachments;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Attachment {
            private String id;

            @JsonProperty("self")
            private String uri;

            @JsonProperty("filename")
            private String fileName;

            public Attachment() {
            }

            public String getUri() {
                return uri;
            }

            public void setUri(String uri) {
                this.uri = uri;
            }

            public String getId() {
                return id;
            }

            public void setId(String id) {
                this.id = id;
            }

            public String getFileName() {
                return fileName;
            }

            public void setFileName(String fileName) {
                this.fileName = fileName;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Attachment that = (Attachment) o;

                if (!id.equals(that.id)) return false;
                if (uri != null ? !uri.equals(that.uri) : that.uri != null) return false;
                return fileName != null ? fileName.equals(that.fileName) : that.fileName == null;
            }

            @Override
            public int hashCode() {
                int result = id.hashCode();
                result = 31 * result + (uri != null ? uri.hashCode() : 0);
                result = 31 * result + (fileName != null ? fileName.hashCode() : 0);
                return result;
            }

            @Override
            public String toString() {
                return "Attachment{" +
                        "fileName='" + fileName + '\'' +
                        '}';
            }
        }
    }
}
