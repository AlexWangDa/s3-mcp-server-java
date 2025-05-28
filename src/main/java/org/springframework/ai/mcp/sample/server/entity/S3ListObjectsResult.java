package org.springframework.ai.mcp.sample.server.entity;

import com.amazonaws.services.s3.model.ObjectListing;

import java.util.ArrayList;
import java.util.List;

public class S3ListObjectsResult {
    private List<S3Object> objects;
    private List<String> folders;
    private String nextMarker;

    public S3ListObjectsResult(ObjectListing objectListing) {
        this.objects=new ArrayList<>();
        this.folders=objectListing.getCommonPrefixes();
        objectListing.getObjectSummaries().forEach(o->{
            S3Object S3Object=new S3Object(o);
            objects.add(S3Object);
        });
        this.nextMarker=objectListing.getNextMarker();
    }

    public List<S3Object> getObjects() {
        return objects;
    }

    public void setObjects(List<S3Object> objects) {
        this.objects = objects;
    }

    public List<String> getFolders() {
        return folders;
    }

    public void setFolders(List<String> folders) {
        this.folders = folders;
    }

    public String getNextMarker() {
        return nextMarker;
    }

    public void setNextMarker(String nextMarker) {
        this.nextMarker = nextMarker;
    }
}
