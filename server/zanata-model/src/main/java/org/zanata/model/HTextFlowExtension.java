package org.zanata.model;

import java.io.Serializable;

import jakarta.persistence.Cacheable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.BatchSize;

@Entity
@Cacheable
@BatchSize(size = 20)
@Table(name = "HTextFlowExtension",
        uniqueConstraints = @UniqueConstraint(
                columnNames = { "text_flow_id", "ext_type" }))
public class HTextFlowExtension implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private HTextFlow textFlow;
    private String type;
    private String json;
    private String searchText;

    public HTextFlowExtension() {
    }

    public HTextFlowExtension(HTextFlow textFlow, String type, String json) {
        this.textFlow = textFlow;
        this.type = type;
        this.json = json;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "text_flow_id", nullable = false)
    public HTextFlow getTextFlow() {
        return textFlow;
    }

    public void setTextFlow(HTextFlow textFlow) {
        this.textFlow = textFlow;
    }

    @NotNull
    @Column(name = "ext_type", nullable = false, length = 64)
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Column(name = "json", columnDefinition = "text")
    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    @Column(name = "search_text", columnDefinition = "text")
    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }
}
