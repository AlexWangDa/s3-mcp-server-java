package org.springframework.ai.mcp.sample.server.entity;

import com.amazonaws.services.s3.model.Bucket;

import java.util.Date;

public class S3Bucket {
    private String location;
    private String owner;
    private Date creationDate;

    public S3Bucket(Bucket bucket) {
        this.owner = bucket.getOwner().getDisplayName();
        this.creationDate = bucket.getCreationDate();
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }
}
