package com.example.DocuMindAI.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(
        name = "document_segments",
        indexes = {
                @Index(name = "idx_segment_doc_id", columnList = "document_id"),
                @Index(name = "idx_segment_order", columnList = "document_id, segment_order")
        }
)
public class DocumentSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "segment_type", nullable = false, length = 30)
    private String segmentType;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "paragraph_index")
    private Integer paragraphIndex;

    @Column(name = "heading", length = 300)
    private String heading;

    @Column(name = "segment_order", nullable = false)
    private int segmentOrder;

    @Column(name = "text", columnDefinition = "TEXT", nullable = false)
    private String text;

    public DocumentSegment() {
    }

    public DocumentSegment(
            String segmentType,
            Integer pageNumber,
            Integer paragraphIndex,
            String heading,
            int segmentOrder,
            String text) {
        this.segmentType = segmentType;
        this.pageNumber = pageNumber;
        this.paragraphIndex = paragraphIndex;
        this.heading = heading;
        this.segmentOrder = segmentOrder;
        this.text = text;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public String getSegmentType() {
        return segmentType;
    }

    public void setSegmentType(String segmentType) {
        this.segmentType = segmentType;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Integer getParagraphIndex() {
        return paragraphIndex;
    }

    public void setParagraphIndex(Integer paragraphIndex) {
        this.paragraphIndex = paragraphIndex;
    }

    public String getHeading() {
        return heading;
    }

    public void setHeading(String heading) {
        this.heading = heading;
    }

    public int getSegmentOrder() {
        return segmentOrder;
    }

    public void setSegmentOrder(int segmentOrder) {
        this.segmentOrder = segmentOrder;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
