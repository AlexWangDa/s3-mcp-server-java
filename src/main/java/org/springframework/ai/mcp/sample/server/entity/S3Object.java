package org.springframework.ai.mcp.sample.server.entity;

import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.util.Date;

public class S3Object {
    private String key;
    private String storageClass;
    private String etag;
    private Date modifyTime;
    private long size;
    public S3Object(S3ObjectSummary o) {
        this.key=o.getKey();
        this.etag=o.getETag();
        this.storageClass=o.getStorageClass();
        this.modifyTime=o.getLastModified();
        this.size=o.getSize();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getStorageClass() {
        return storageClass;
    }

    public void setStorageClass(String storageClass) {
        this.storageClass = storageClass;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public Date getModifyTime() {
        return modifyTime;
    }

    public void setModifyTime(Date modifyTime) {
        this.modifyTime = modifyTime;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
