package com.company.codeinsight.modules.draft.service;

import com.company.codeinsight.modules.draft.entity.*;
import java.util.List;

public interface DraftService {

    List<KnowledgeDraft> listDraftsByWorkspace(Long workspaceId);

    DraftWorkspace getWorkspaceByTaskId(Long taskId);

    KnowledgeDraft getDraftById(Long draftId);

    String getDraftContent(Long draftId);

    void saveDraft(Long draftId, String content, String author, String remark);

    void autoSaveDraft(Long draftId, String content, String author);

    void confirmDraft(Long draftId, String author);

    void rejectDraft(Long draftId, String author, String comment);

    List<DraftRevision> getRevisions(Long draftId);

    List<DraftReviewComment> getComments(Long draftId);

    List<DraftSourceReference> getSourceReferences(Long draftId);
}
