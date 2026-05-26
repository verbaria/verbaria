package org.zanata.rest.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class FilterFields implements Serializable {
    private static final long serialVersionUID = 1L;

    private String searchString;
    private String resId;
    private String changedBefore;
    private String changedAfter;
    private String lastModifiedByUser;
    private String sourceComment;
    private String transComment;
    private String msgContext;

    public String getSearchString() {
        return searchString;
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }

    public String getResId() {
        return resId;
    }

    public void setResId(String resId) {
        this.resId = resId;
    }

    public String getChangedBefore() {
        return changedBefore;
    }

    public void setChangedBefore(String changedBefore) {
        this.changedBefore = changedBefore;
    }

    public String getChangedAfter() {
        return changedAfter;
    }

    public void setChangedAfter(String changedAfter) {
        this.changedAfter = changedAfter;
    }

    public String getLastModifiedByUser() {
        return lastModifiedByUser;
    }

    public void setLastModifiedByUser(String lastModifiedByUser) {
        this.lastModifiedByUser = lastModifiedByUser;
    }

    public String getSourceComment() {
        return sourceComment;
    }

    public void setSourceComment(String sourceComment) {
        this.sourceComment = sourceComment;
    }

    public String getTransComment() {
        return transComment;
    }

    public void setTransComment(String transComment) {
        this.transComment = transComment;
    }

    public String getMsgContext() {
        return msgContext;
    }

    public void setMsgContext(String msgContext) {
        this.msgContext = msgContext;
    }

}
