import { Building2Icon } from "lucide-react";
import { Form, redirect, useNavigation, type ActionFunctionArgs, type LoaderFunctionArgs } from "react-router";

import { PageHeader } from "~/components/page-header";
import { SubmitButton } from "~/components/submit-button";
import { Alert, AlertDescription, AlertTitle } from "~/components/ui/alert";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "~/components/ui/card";
import { Input } from "~/components/ui/input";
import { Label } from "~/components/ui/label";
import { findOrganization, provisionOrganization } from "~/lib/organization.server";
import { attempt } from "~/lib/api/server";
import { errorOf, failure, fieldError } from "~/lib/forms";

/** Already set up? Then there is nothing to do here. */
export async function loader(args: LoaderFunctionArgs) {
  const organization = await findOrganization(args);
  return organization ? redirect("/") : { ready: true };
}

export async function action(args: ActionFunctionArgs) {
  const form = await args.request.formData();
  const name = String(form.get("name") ?? "").trim();
  if (!name) {
    return failure("VALIDATION_FAILED", "Request validation failed", [
      { field: "name", message: "must not be blank" },
    ]);
  }

  const result = await attempt(() => provisionOrganization(args, name));
  return result.ok ? redirect("/") : result;
}

export default function Setup() {
  const navigation = useNavigation();

  return (
    <>
      <PageHeader
        title="Name your organization"
        description="Signing in also brings you into your organization, and there is one per account."
      />

      <div className="max-w-xl">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Building2Icon className="size-4" />
              Your organization
            </CardTitle>
            <CardDescription>
              This is the tenant that owns your projects, documents and chats. It is created once and
              cannot be duplicated under the same sign-in.
            </CardDescription>
          </CardHeader>
          <Form method="post">
            <CardContent className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="name">Organization name</Label>
                <Input id="name" name="name" placeholder="Acme Ltd." required disabled={navigation.state !== "idle"} />
                {fieldError(undefined, "name")}
              </div>
            </CardContent>
            <CardFooter>
              <SubmitButton className="w-full">
                {navigation.state === "idle" ? "Create my organization" : "Creating…"}
              </SubmitButton>
            </CardFooter>
          </Form>
        </Card>
      </div>
    </>
  );
}
