package cl.uss.ca.api.dto;

public class EventsModuleStatusDto {
    public String module = "EVENTS";
    public String backend = "openscience-uss";
    public String catalog = "uss-ca";
    public boolean tablesAvailable;
    public boolean directoryAvailable;
    public int publishedCount;
    public String message;
}
