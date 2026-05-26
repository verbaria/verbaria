package org.zanata.rest.dto;

public class ChunkUploadResponse {
    private Long uploadId;
    private int acceptedChunks;
    private boolean expectingMore;
    private String successMessage;
    private String errorMessage;

    public ChunkUploadResponse() {
    }

    /**
     * Create a response indicating that something went wrong before starting an
     * upload.
     *
     * @param errorMessage
     *            detailing what went wrong
     */
    public ChunkUploadResponse(String errorMessage) {
        this.errorMessage = errorMessage;
        acceptedChunks = 0;
        expectingMore = false;
    }

    public ChunkUploadResponse(long uploadId, int accepted,
            boolean expectingMore, String successMessage) {
        this.uploadId = uploadId;
        this.acceptedChunks = accepted;
        this.expectingMore = expectingMore;
        this.successMessage = successMessage;
    }

    public Long getUploadId() {
        return uploadId;
    }

    public void setUploadId(Long uploadId) {
        this.uploadId = uploadId;
    }

    public int getAcceptedChunks() {
        return acceptedChunks;
    }

    public void setAcceptedChunks(int acceptedChunks) {
        this.acceptedChunks = acceptedChunks;
    }

    public boolean isExpectingMore() {
        return expectingMore;
    }

    public void setExpectingMore(boolean expectingMore) {
        this.expectingMore = expectingMore;
    }

    public String getSuccessMessage() {
        return successMessage;
    }

    public void setSuccessMessage(String message) {
        successMessage = message;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "ChunkUploadResponse: uploadId=" + uploadId + " acceptedChunks="
                + acceptedChunks + " expectingMore=" + expectingMore
                + " successMessage=\"" + successMessage + "\" errorMessage=\""
                + errorMessage + "\".";
    }
}
