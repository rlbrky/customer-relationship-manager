import { useCallback, useEffect, useState } from 'react'
import { ActivityForm } from './ActivityForm'
import { ConflictBanner } from './ConflictBanner'
import { ActivityTimeline } from './ActivityTimeline'
import { Pagination } from './Pagination'
import { ApiError } from '../api/client'
import * as activitiesApi from '../api/activities'
import type {
  Activity,
  ActivityCreateRequest,
  ActivityType,
  ActivityUpdateRequest,
} from '../types/activity'
import { ACTIVITY_TYPES, activityTypeLabel } from '../types/activity'
import type { Contact } from '../types/contact'

type Editor = { kind: 'none' } | { kind: 'create' } | { kind: 'edit'; activity: Activity }

const PAGE_SIZE = 15

interface AccountActivitiesProps {
  accountId: number
  /** Contacts on this account, for the "with whom" picker. */
  contacts: Contact[]
}

/**
 * Owns everything activity-related for one account: its own fetching, paging,
 * type filter and forms. Kept separate so AccountDetailPage stays readable.
 */
export function AccountActivities({ accountId, contacts }: AccountActivitiesProps) {
  const [activities, setActivities] = useState<Activity[]>([])
  const [pageNumber, setPageNumber] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  const [typeFilter, setTypeFilter] = useState<ActivityType | ''>('')
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState<string | null>(null)

  const [editor, setEditor] = useState<Editor>({ kind: 'none' })
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)

  const load = useCallback(
    async (page: number) => {
      setLoading(true)
      setListError(null)
      try {
        const result = await activitiesApi.fetchActivities(
          accountId,
          page,
          PAGE_SIZE,
          'occurredAt,desc', // newest first — the order the composite index serves
          typeFilter || undefined,
        )
        setActivities(result.content)
        setPageNumber(result.page.number)
        setTotalPages(result.page.totalPages)
        setTotalElements(result.page.totalElements)
      } catch (err) {
        setListError(err instanceof ApiError ? err.message : 'Could not load activities.')
      } finally {
        setLoading(false)
      }
    },
    [accountId, typeFilter],
  )

  useEffect(() => {
    void load(0)
  }, [load])

  async function runMutation(action: () => Promise<unknown>, backToPage = pageNumber) {
    setSubmitting(true)
    setFormError(null)
    try {
      await action()
      setEditor({ kind: 'none' })
      await load(backToPage)
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'Something went wrong.')
    } finally {
      setSubmitting(false)
    }
  }

  function handleCreate(request: ActivityCreateRequest) {
    // newest-first ordering means a fresh entry lands on page 1
    void runMutation(() => activitiesApi.createActivity(accountId, request), 0)
  }

  /** Not runMutation: a 409 must leave the editor open with the user's edits. */
  async function handleUpdate(id: number, request: ActivityUpdateRequest) {
    setSubmitting(true)
    setFormError(null)
    setConflict(false)
    try {
      await activitiesApi.updateActivity(id, request)
      setEditor({ kind: 'none' })
      await load(pageNumber)
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setConflict(true)
      } else {
        setFormError(err instanceof ApiError ? err.message : 'Something went wrong.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function loadCurrentValues(id: number) {
    try {
      setEditor({ kind: 'edit', activity: await activitiesApi.fetchActivity(id) })
      setConflict(false)
    } catch {
      setFormError('Could not load the current values.')
    }
  }

  function handleDelete(target: Activity) {
    if (!window.confirm(`Delete "${target.subject}"?`)) return
    const page = activities.length === 1 && pageNumber > 0 ? pageNumber - 1 : pageNumber
    void runMutation(() => activitiesApi.deleteActivity(target.id), page)
  }

  /**
   * Completion is a full PUT — the API has no partial update — so it carries the
   * version of the row as it was last listed.
   *
   * A 409 here needs the opposite treatment to the form: there is nothing typed to
   * protect, just a stale checkbox. Refresh the list and say so; the user can click
   * again against the current state.
   */
  async function handleToggleComplete(target: Activity) {
    setFormError(null)
    try {
      await activitiesApi.updateActivity(target.id, {
        version: target.version,
        type: target.type,
        subject: target.subject,
        notes: target.notes,
        occurredAt: target.occurredAt,
        dueAt: target.dueAt,
        contactId: target.contactId,
        completed: !target.completed,
      })
      await load(pageNumber)
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setFormError('That activity changed elsewhere — the list has been refreshed.')
        await load(pageNumber)
      } else {
        setFormError(err instanceof ApiError ? err.message : 'Something went wrong.')
      }
    }
  }

  return (
    <>
      <div className="content__head content__head--sub">
        <h2 className="section__title">Activity</h2>
        <div className="head__tools">
          <select
            className="field__input filters__item"
            value={typeFilter}
            onChange={(e) => setTypeFilter(e.target.value as ActivityType | '')}
            aria-label="Filter by activity type"
          >
            <option value="">All types</option>
            {ACTIVITY_TYPES.map((type) => (
              <option key={type} value={type}>{activityTypeLabel(type)}s</option>
            ))}
          </select>
          {editor.kind === 'none' && (
            <button
              className="btn btn--primary"
              type="button"
              onClick={() => { setFormError(null); setEditor({ kind: 'create' }) }}
            >
              Log activity
            </button>
          )}
        </div>
      </div>

      {editor.kind === 'create' && (
        <ActivityForm
          mode="create"
          contacts={contacts}
          submitting={submitting}
          error={formError}
          onSubmit={handleCreate}
          onCancel={() => setEditor({ kind: 'none' })}
        />
      )}

      {editor.kind === 'edit' && (
        <>
          {conflict && (
            <ConflictBanner
              noun="activity"
              onReload={() => void loadCurrentValues(editor.activity.id)}
            />
          )}

          <ActivityForm
            key={editor.activity.version}
            mode="edit"
            activity={editor.activity}
            contacts={contacts}
            submitting={submitting}
            error={formError}
            onSubmit={(request) => void handleUpdate(editor.activity.id, request)}
            onCancel={() => { setConflict(false); setEditor({ kind: 'none' }) }}
          />
        </>
      )}

      {editor.kind === 'none' && formError && (
        <p className="form__error" role="alert">{formError}</p>
      )}

      {loading && <p className="card__hint">Loading activity…</p>}
      {listError && <p className="form__error" role="alert">{listError}</p>}

      {!loading && !listError && (
        <>
          <ActivityTimeline
            activities={activities}
            onEdit={(activity) => { setFormError(null); setEditor({ kind: 'edit', activity }) }}
            onDelete={handleDelete}
            onToggleComplete={handleToggleComplete}
            emptyMessage={
              typeFilter
                ? `No ${activityTypeLabel(typeFilter).toLowerCase()} activity on this account.`
                : 'Nothing logged yet. Record a call, email, meeting, note or task.'
            }
          />

          <Pagination
            number={pageNumber}
            totalPages={totalPages}
            totalElements={totalElements}
            onChange={(page) => void load(page)}
            label="activity"
            labelPlural="activities"
          />
        </>
      )}
    </>
  )
}
