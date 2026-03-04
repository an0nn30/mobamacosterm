package com.github.an0nn30.conch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerFolder {
    private String id;
    private String name;
    private List<ServerEntry> servers;

    public ServerFolder() {
        this.id = UUID.randomUUID().toString();
        this.servers = new ArrayList<>();
    }

    public ServerFolder(String name) {
        this();
        this.name = name;
    }

    public String getId()                        { return id; }
    public void   setId(String id)               { this.id = id; }
    public String getName()                      { return name; }
    public void   setName(String name)           { this.name = name; }
    public List<ServerEntry> getServers()        { return servers; }
    public void   setServers(List<ServerEntry> s){ this.servers = s; }

    @Override
    public String toString() { return name; }
}
