package cl.uss.ca.db;

public class ResearchOutputCargaResumen {
    private Integer informadosPorPure;
    private int recuperados;
    private int procesadosOk;
    private int conError;
    private int conDoi;
    private int sinDoi;
    private int conJournal;
    private int sinJournal;
    private int contributorsInternos;
    private int contributorsExternos;
    private int publicationStatuses;
    private int contributors;
    private int electronicVersions;
    private int identifiers;
    private int identifiersDuplicadosOmitidos;
    private int links;
    private int journals;
    private int externalOrganizations;
    private int keywordGroups;
    private int keywords;

    public void setInformadosPorPure(Integer informadosPorPure) {
        this.informadosPorPure = informadosPorPure;
    }

    public void registrarRecuperado() {
        recuperados++;
    }

    public void registrarError() {
        conError++;
    }

    public void registrarOk(cl.uss.ca.pure.ro.ResearchOutput output, int journalsInsertados) {
        procesadosOk++;
        if (output.tieneDoi()) {
            conDoi++;
        } else {
            sinDoi++;
        }
        if (output.getJournal() != null) {
            conJournal++;
        } else {
            sinJournal++;
        }
        for (var contributor : output.getContributors()) {
            if (contributor.esInterno()) {
                contributorsInternos++;
            } else if (contributor.esExterno()) {
                contributorsExternos++;
            }
        }
        publicationStatuses += output.getStatuses().size();
        contributors += output.getContributors().size();
        electronicVersions += output.getElectronicVersions().size();
        identifiers += output.getIdentifiers().size();
        identifiersDuplicadosOmitidos += output.getIdentifiersDuplicadosOmitidos();
        links += output.getLinks().size();
        journals += journalsInsertados;
        externalOrganizations += output.getExternalOrganizationUuids().size();
        keywordGroups += output.getKeywordGroups().size();
        keywords += output.getKeywordGroups().stream()
                .mapToInt(group -> group.getKeywords().size())
                .sum();
    }

    public void imprimir() {
        System.out.println("Carga de Research Outputs Pure finalizada");
        System.out.println();
        System.out.println("Research Outputs informados por Pure: " + informadosPorPure);
        System.out.println("Research Outputs recuperados: " + recuperados);
        System.out.println("Research Outputs procesados correctamente: " + procesadosOk);
        System.out.println("Research Outputs con error: " + conError);
        System.out.println();
        System.out.println("Con DOI: " + conDoi);
        System.out.println("Sin DOI: " + sinDoi);
        System.out.println();
        System.out.println("Con Journal: " + conJournal);
        System.out.println("Sin Journal: " + sinJournal);
        System.out.println();
        System.out.println("Contributors internos: " + contributorsInternos);
        System.out.println("Contributors externos: " + contributorsExternos);
        System.out.println();
        System.out.println("Publication Statuses: " + publicationStatuses);
        System.out.println("Contributors: " + contributors);
        System.out.println("Electronic Versions: " + electronicVersions);
        System.out.println("Identifiers insertados: " + identifiers);
        System.out.println("Identifiers duplicados omitidos: " + identifiersDuplicadosOmitidos);
        System.out.println("Links: " + links);
        System.out.println("Journals: " + journals);
        System.out.println("External Organizations: " + externalOrganizations);
        System.out.println("Keyword Groups: " + keywordGroups);
        System.out.println("Keywords: " + keywords);
    }

    public Integer getInformadosPorPure() {
        return informadosPorPure;
    }

    public int getRecuperados() {
        return recuperados;
    }

    public int getConError() {
        return conError;
    }
}
