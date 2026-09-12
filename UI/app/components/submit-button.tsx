import { Loader2Icon } from "lucide-react";
import { useNavigation } from "react-router";

import { Button } from "~/components/ui/button";

interface SubmitButtonProps extends React.ComponentProps<typeof Button> {
  /** Pending state of the fetcher driving this button, when it is not a page navigation. */
  pending?: boolean;
}

/** Submit button that shows progress while the surrounding form is in flight. */
export function SubmitButton({ children, pending, disabled, ...props }: SubmitButtonProps) {
  const navigation = useNavigation();
  const busy = pending ?? navigation.state !== "idle";

  return (
    <Button type="submit" disabled={busy || disabled} {...props}>
      {busy && <Loader2Icon className="animate-spin" data-icon="inline-start" />}
      {children}
    </Button>
  );
}
