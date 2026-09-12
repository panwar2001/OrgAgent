import { redirect, type LoaderFunctionArgs } from "react-router";

import { ApiError } from "~/lib/api/client";
import { api } from "~/lib/api/server";
import { requireUser, type SessionUser } from "~/lib/auth.server";
import type { Organization } from "~/lib/api/types";

/**
 * One organization per sign-in.
 *
 * Signing in *is* joining an organization here, so a user never picks between organizations and can
 * never create a second one. The mapping is derived rather than stored: the slug is a pure function
 * of the Clerk user id, so the same person always resolves to the same organization and no extra
 * bookkeeping table is needed.
 *
 * A user who has never signed in before has no organization yet; they name it once, on `/setup`.
 */

export interface OrganizationContext {
  user: SessionUser;
  organization: Organization;
}

/** The slug an organization gets from its owner's Clerk user id. */
export function slugFor(userId: string): string {
  return `user-${userId}`.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 120);
}

/** The signed-in user's organization, or null when they have not set one up yet. */
export async function findOrganization(args: LoaderFunctionArgs): Promise<Organization | null> {
  const user = await requireUser(args);
  try {
    return await api(args.context).getOrganizationBySlug(slugFor(user.userId));
  }
  catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      return null;
    }
    throw error;
  }
}

/**
 * The signed-in user's organization.
 *
 * @throws a redirect to `/setup` on a first visit, so every page can assume an organization exists
 */
export async function requireOrganization(args: LoaderFunctionArgs): Promise<OrganizationContext> {
  const user = await requireUser(args);
  const organization = await findOrganization(args);
  if (!organization) {
    throw redirect("/setup");
  }
  return { user, organization };
}

/**
 * Creates the organization for a first-time user.
 *
 * The slug is fixed by their identity, so a concurrent request that already created it produces a
 * conflict we simply treat as "done".
 */
export async function provisionOrganization(args: LoaderFunctionArgs, name: string): Promise<Organization> {
  const user = await requireUser(args);
  const slug = slugFor(user.userId);
  const client = api(args.context);
  try {
    return await client.createOrganization({ name, slug });
  }
  catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      return client.getOrganizationBySlug(slug);
    }
    throw error;
  }
}
