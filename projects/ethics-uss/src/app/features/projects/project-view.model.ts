export interface ProjectCommitteeView {
  id: string;
  shortNameKey: string;
  fullNameKey: string;
  code: string;
  status: string;
  statusLabelKey: string | null;
  receivedAt: string;
  expiresAt: string | null;
  daysLeft: number | null;
  approvalUntil: string | null;
}

export interface ProjectViewModel {
  id: string;
  title: string;
  code: string;
  funding: string;
  researcherName: string | null;
  submittedAt: string;
  overallStatus: string | null;
  committees: ProjectCommitteeView[];
  nextExpiration: { date: string; committeeKey: string } | null;
}
