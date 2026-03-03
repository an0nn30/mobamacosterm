package com.mobamacos.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {
    private List<ServerFolder> folders = new ArrayList<>();
    private String  theme              = "intellij";
    private int     fontSize           = 14;
    private String  fontName           = "";
    private String  terminalBackground = "#000000";
    private String  terminalForeground = "#FFFFFF";
    // Session restore
    private List<String>  lastSessionKeys   = new ArrayList<>();   // "user@host:port"
    private boolean       dontAskResume     = false;

    public List<ServerFolder> getFolders()            { return folders; }
    public void setFolders(List<ServerFolder> folders){ this.folders = folders; }
    public String getTheme()                          { return theme; }
    public void   setTheme(String theme)              { this.theme = theme; }
    public int    getFontSize()                       { return fontSize; }
    public void   setFontSize(int fontSize)           { this.fontSize = fontSize; }
    public String getFontName()                       { return fontName; }
    public void   setFontName(String fontName)        { this.fontName = fontName; }
    public String getTerminalBackground()             { return terminalBackground; }
    public void   setTerminalBackground(String c)     { this.terminalBackground = c; }
    public String getTerminalForeground()             { return terminalForeground; }
    public void   setTerminalForeground(String c)     { this.terminalForeground = c; }
    public List<String> getLastSessionKeys()          { return lastSessionKeys; }
    public void   setLastSessionKeys(List<String> k)  { this.lastSessionKeys = k; }
    public boolean isDontAskResume()                  { return dontAskResume; }
    public void   setDontAskResume(boolean b)         { this.dontAskResume = b; }
}
