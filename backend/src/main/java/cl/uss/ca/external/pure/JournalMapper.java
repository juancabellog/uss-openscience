package cl.uss.ca.external.pure;

import cl.uss.ca.external.pure.json.ClassificationRef;
import cl.uss.ca.external.pure.json.IdentifierItem;
import cl.uss.ca.external.pure.json.IssnItem;
import cl.uss.ca.external.pure.json.JournalItem;
import cl.uss.ca.external.pure.json.JournalTitleItem;
import cl.uss.ca.external.pure.json.KeywordContainerItem;
import cl.uss.ca.external.pure.json.KeywordGroupItem;
import cl.uss.ca.external.pure.json.OrganizationRef;
import cl.uss.ca.external.pure.json.WorkflowItem;
import cl.uss.ca.external.pure.model.JournalClassificationData;
import cl.uss.ca.external.pure.model.JournalIdentifierData;
import cl.uss.ca.external.pure.model.JournalIssnData;
import cl.uss.ca.external.pure.model.JournalKeywordData;
import cl.uss.ca.external.pure.model.JournalKeywordGroupData;
import cl.uss.ca.external.pure.model.JournalTitleData;
import cl.uss.ca.external.pure.model.PureJournal;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@ApplicationScoped
public class JournalMapper {

    public PureJournal mapear(JournalItem item) {
        if (item == null) {
            return null;
        }
        String uuid = LocalizedTexts.trimToNull(item.getUuid());
        if (uuid == null) {
            return null;
        }
        PureJournal journal = new PureJournal();
        journal.setPureId(item.getPureId());
        journal.setUuid(uuid);
        journal.setCreatedBy(LocalizedTexts.trimToNull(item.getCreatedBy()));
        journal.setCreatedDatePure(parseDateTime(item.getCreatedDate()));
        journal.setModifiedBy(LocalizedTexts.trimToNull(item.getModifiedBy()));
        journal.setModifiedDatePure(parseDateTime(item.getModifiedDate()));
        journal.setVersion(LocalizedTexts.trimToNull(item.getVersion()));
        journal.setSystemName(LocalizedTexts.trimToNull(item.getSystemName()));
        OrganizationRef publisher = item.getPublisher();
        if (publisher != null) {
            journal.setPublisherUuid(LocalizedTexts.trimToNull(publisher.getUuid()));
            journal.setPublisherSystemName(LocalizedTexts.trimToNull(publisher.getSystemName()));
        }
        ClassificationRef type = item.getType();
        if (type != null) {
            journal.setTypeUri(LocalizedTexts.uri(type));
            journal.setTypeEn(LocalizedTexts.en(type.getTerm()));
            journal.setTypeEs(LocalizedTexts.es(type.getTerm()));
        }
        WorkflowItem workflow = item.getWorkflow();
        if (workflow != null) {
            journal.setWorkflowStep(LocalizedTexts.trimToNull(workflow.getStep()));
            journal.setWorkflowDescriptionEn(LocalizedTexts.en(workflow.getDescription()));
            journal.setWorkflowDescriptionEs(LocalizedTexts.es(workflow.getDescription()));
        }
        mapTitles(item.getTitles(), journal);
        mapIssns(item.getIssns(), journal);
        mapIdentifiers(item.getIdentifiers(), journal);
        mapKeywordGroups(item.getKeywordGroups(), journal);
        return journal;
    }

    private static void mapTitles(List<JournalTitleItem> items, PureJournal journal) {
        for (JournalTitleItem item : empty(items)) {
            if (item == null) {
                continue;
            }
            String title = LocalizedTexts.trimToNull(item.getTitle());
            if (title == null) {
                continue;
            }
            JournalTitleData data = new JournalTitleData();
            data.setPureId(item.getPureId());
            data.setTitle(title);
            journal.getTitles().add(data);
        }
    }

    private static void mapIssns(List<IssnItem> items, PureJournal journal) {
        for (IssnItem item : empty(items)) {
            if (item == null) {
                continue;
            }
            String issn = LocalizedTexts.trimToNull(item.getIssn());
            if (issn == null) {
                continue;
            }
            JournalIssnData data = new JournalIssnData();
            data.setPureId(item.getPureId());
            data.setIssn(issn);
            journal.getIssns().add(data);
        }
    }

    private static void mapIdentifiers(List<IdentifierItem> items, PureJournal journal) {
        for (IdentifierItem item : empty(items)) {
            if (item == null) {
                continue;
            }
            JournalIdentifierData data = new JournalIdentifierData();
            data.setPureId(item.getPureId());
            data.setTypeDiscriminator(LocalizedTexts.trimToNull(item.getTypeDiscriminator()));
            data.setIdSource(LocalizedTexts.trimToNull(item.getIdSource()));
            data.setValue(firstNonBlank(item.getValue(), item.getId()));
            ClassificationRef type = item.getType();
            if (type != null) {
                data.setTypeUri(LocalizedTexts.uri(type));
                data.setTypeEn(LocalizedTexts.en(type.getTerm()));
                data.setTypeEs(LocalizedTexts.es(type.getTerm()));
            }
            journal.getIdentifiers().add(data);
        }
    }

    private static void mapKeywordGroups(List<KeywordGroupItem> items, PureJournal journal) {
        for (KeywordGroupItem item : empty(items)) {
            if (item == null) {
                continue;
            }
            JournalKeywordGroupData group = new JournalKeywordGroupData();
            group.setPureId(item.getPureId());
            group.setTypeDiscriminator(LocalizedTexts.trimToNull(item.getTypeDiscriminator()));
            group.setLogicalName(LocalizedTexts.trimToNull(item.getLogicalName()));
            group.setNameEn(LocalizedTexts.en(item.getName()));
            group.setNameEs(LocalizedTexts.es(item.getName()));
            for (ClassificationRef classification : empty(item.getClassifications())) {
                if (classification == null || LocalizedTexts.uri(classification) == null) {
                    continue;
                }
                JournalClassificationData data = new JournalClassificationData();
                data.setUri(LocalizedTexts.uri(classification));
                data.setTermEn(LocalizedTexts.en(classification.getTerm()));
                data.setTermEs(LocalizedTexts.es(classification.getTerm()));
                group.getClassifications().add(data);
            }
            for (KeywordContainerItem container : empty(item.getKeywordContainers())) {
                if (container == null) {
                    continue;
                }
                JournalKeywordData data = new JournalKeywordData();
                data.setPureId(container.getPureId());
                data.setState(LocalizedTexts.trimToNull(container.getState()));
                data.setOrigin(LocalizedTexts.trimToNull(container.getOrigin()));
                ClassificationRef structured = container.getStructuredKeyword();
                if (structured != null) {
                    data.setKeywordUri(LocalizedTexts.uri(structured));
                    data.setKeywordEn(LocalizedTexts.en(structured.getTerm()));
                    data.setKeywordEs(LocalizedTexts.es(structured.getTerm()));
                }
                group.getKeywords().add(data);
            }
            journal.getKeywordGroups().add(group);
        }
    }

    private static OffsetDateTime parseDateTime(String raw) {
        String value = LocalizedTexts.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String firstNonBlank(String left, String right) {
        String first = LocalizedTexts.trimToNull(left);
        return first != null ? first : LocalizedTexts.trimToNull(right);
    }

    private static <T> List<T> empty(List<T> items) {
        return items == null ? List.of() : items;
    }
}
