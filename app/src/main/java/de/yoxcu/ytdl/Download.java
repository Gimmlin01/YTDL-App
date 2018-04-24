package de.yoxcu.ytdl;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by yoxcu on 22.03.18.
 */

public class Download {

    private double prog;
    private File file;
    private Integer dl_id;
    private Integer startId;
    private String url;
    private String filename;
    private Double status;
    private String conv;
    private Integer not_id;
    private long threadId;
    private String downloadUrl;

    public Download(String url, String conv) {
        this.url = url;
        this.conv = conv;
        this.not_id = (int) System.currentTimeMillis() % 1000;
        this.dl_id = -1;
    }

    public long getThreadId() {
        return threadId;
    }

    public void setThreadId(long threadId) {
        this.threadId = threadId;
    }


    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public double getProg() {
        return prog;
    }

    public void setProg(double prog) {
        this.prog = prog;
    }

    public Integer getDl_id() {
        return dl_id;
    }

    public Integer getNot_id() {
        return (not_id==null)?dl_id:not_id;
    }

    public void setStartId(int startId) {
        this.startId = startId;
    }

    public Integer getStartId() {
        return startId;
    }

    public void setDl_id(int dl_id) {
        this.dl_id = dl_id;
    }

    public Double getStatus() {
        return status;
    }

    public void setStatus(Double status) {
        this.status= status;
    }


    public String getFilename() {
        return filename;
    }


    public String getConv() {
        return conv;
    }

    public String getUrl() {
        return url;
    }

    public String getDownloadUrl() {
        if (downloadUrl!=null){
            return downloadUrl;
        }else {
            return "downloads/" + dl_id;
        }
    }

    public void setDownloadUrl(String url) {
        this.downloadUrl=url;
    }

    public void setFilename(String filename) {
        this.filename=filename;
    }


    public void updateDownload(Download dl) {
            if (this.dl_id ==-1 || this.dl_id.equals(dl.getDl_id())) {
                if (dl.getDl_id() != null) {
                    this.dl_id = dl.getDl_id();
                    this.downloadUrl="downloads/" + dl_id;
                }
                if (dl.getUrl() != null) {
                    this.url = dl.getUrl();
                }
                if (dl.getFilename() != null) {
                    this.filename = dl.getFilename();
                }
                if (dl.getStatus() != null) {
                    this.status = dl.getStatus();
                }
                if (dl.getConv() != null) {
                    this.conv = dl.getConv();
                }
            }

    }


    public void updateFromJson(String json) {
        updateDownload(fromJson(json));
    }

    public String toJson() {
        try {
            Gson gson = new GsonBuilder().create();
            return gson.toJson(this);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public String toServerJson() {
        try {
            Gson gson = new GsonBuilder().create();
            return gson.toJson(new Download(url,conv));
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static Download fromJson(String json) {
        try {
            Gson gson = new GsonBuilder().create();
            return gson.fromJson(json, Download.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ArrayList<Download> dlListFromJson(String json) {
        try {
            Gson gson = new GsonBuilder().create();
            return gson.fromJson(json, new TypeToken<ArrayList<Download>>() {
            }.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}


