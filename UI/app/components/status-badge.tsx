import { Badge } from "~/components/ui/badge";
import type { DocumentStatus, OrganizationStatus, ProjectStatus } from "~/lib/api/types";

type AnyStatus = OrganizationStatus | ProjectStatus | DocumentStatus;

const VARIANT: Record<AnyStatus, "default" | "secondary" | "destructive" | "outline"> = {
  ACTIVE: "default",
  INDEXED: "default",
  SUSPENDED: "outline",
  PENDING: "outline",
  ARCHIVED: "secondary",
  FAILED: "destructive",
};

export function StatusBadge({ status }: { status: AnyStatus }) {
  return <Badge variant={VARIANT[status] ?? "outline"}>{status.toLowerCase()}</Badge>;
}
