import { useState, useCallback } from "react";
import { DndContext, PointerSensor, useSensor, useSensors, closestCenter, useDroppable } from "@dnd-kit/core";
import type { DragEndEvent, DragOverEvent } from "@dnd-kit/core";
import {
  SortableContext,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { useSSESubscription } from "@/shared/hooks/useSSEContext";
import { EventType } from "@/shared/types/events";
import { StickyNote } from '@/modules/facilitation/components/retro/StickyNote';
import type { StickyNoteData } from '@/modules/facilitation/components/retro/StickyNote';
import type { StepComponentProps } from "@/modules/facilitation/components/ComponentRouter";
import type { components } from "@/shared/types/api.d.ts";
import { useClusters, submitColumnResponse, toggleVote, updateResponse, mergeResponses, unmergeResponse } from "@/modules/facilitation/hooks/api/useColumnBoard";
import { useCurrentUser } from "@/modules/auth/hooks/useAuth";

type ColumnResponseDto = components["schemas"]["ColumnResponseDto"];
type ClusterGroupsDto = components["schemas"]["ClusterGroupsDto"];

interface ColumnConfig {
  id: string;
  title: string;
  color?: string;
  placeholder?: string;
}

interface Capabilities {
  allowInput?: boolean;
  allowMerging?: boolean;
  allowVoting?: boolean;
  showContent?: boolean;
  showVotes?: boolean;
  showAuthor?: boolean;
  maxLength?: number;
  numberOfVotes?: number;
}

interface MultiColumnBoardConfig {
  columns: ColumnConfig[];
  capabilities?: Capabilities;
  display?: {
    showVotes?: boolean;
    showAuthor?: boolean;
  };
  cardConfig?: {
    maxLength?: number;
    placeholder?: string;
  };
}

function allResponses(clusters: ClusterGroupsDto): ColumnResponseDto[] {
  const result: ColumnResponseDto[] = [];
  const clustered = clusters.clustered ?? {};
  for (const notes of Object.values(clustered)) {
    result.push(...notes);
  }
  result.push(...(clusters.unclustered ?? []));
  return result;
}

interface ColumnDropZoneProps {
  columnId: string;
  children: React.ReactNode;
}

function ColumnDropZone({ columnId, children }: ColumnDropZoneProps) {
  const { setNodeRef, isOver } = useDroppable({ id: columnId });
  return (
    <div
      ref={setNodeRef}
      className={[
        "min-h-[120px] flex flex-col gap-2 rounded-lg p-2 transition-colors",
        isOver ? "bg-amber-50 ring-2 ring-amber-300" : "bg-transparent",
      ].join(" ")}
    >
      {children}
    </div>
  );
}

interface AddNoteFormProps {
  columnId: string;
  placeholder?: string | undefined;
  maxLength: number;
  retroId: string;
  stepId: number;
  onSuccess: () => void;
}

function AddNoteForm({ columnId, placeholder, maxLength, retroId, stepId, onSuccess }: AddNoteFormProps) {
  const [content, setContent] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = content.trim();
    if (!trimmed) return;
    setSubmitting(true);
    try {
      await submitColumnResponse(retroId, stepId, columnId, trimmed);
      setContent("");
      onSuccess();
    } finally {
      setSubmitting(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void handleSubmit(e as unknown as React.FormEvent);
    }
  };

  return (
    <form onSubmit={(e) => void handleSubmit(e)} className="mt-2">
      <textarea
        data-testid={`note-input-${columnId}`}
        name="content"
        value={content}
        onChange={(e) => setContent(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder={placeholder ?? "Add a note…"}
        maxLength={maxLength}
        rows={2}
        disabled={submitting}
        className="w-full text-sm border border-gray-200 rounded-lg px-3 py-2 resize-none focus:outline-none focus:ring-2 focus:ring-amber-400 disabled:opacity-50"
      />
      <div className="flex justify-end mt-1">
        <button
          data-testid={`submit-note-${columnId}`}
          type="submit"
          disabled={!content.trim() || submitting}
          className="text-xs px-3 py-1 rounded-lg bg-amber-500 text-white font-medium hover:bg-amber-600 disabled:opacity-40 transition-colors"
        >
          {submitting ? "Adding…" : "➕"}
        </button>
      </div>
    </form>
  );
}

export function MultiColumnBoard({ retroId, stepId, componentConfig }: StepComponentProps) {
  const config = componentConfig as unknown as MultiColumnBoardConfig;
  const columns = (config.columns ?? []).map((col) => ({
    ...col,
    id: col.id ?? col.title.toLowerCase(),
  }));
  const caps = config.capabilities ?? {};
  const display = config.display ?? {};
  const cardConfig = config.cardConfig ?? {};

  const allowInput = caps.allowInput !== false;
  const allowMerging = caps.allowMerging === true;
  const allowVoting = caps.allowVoting === true;
  const showVotes = display.showVotes === true || caps.showVotes === true;
  const showAuthor = display.showAuthor === true || caps.showAuthor === true;
  const maxLength = cardConfig.maxLength ?? caps.maxLength ?? 500;
  const showContent = caps.showContent === true;

  const [dragOverColumn, setDragOverColumn] = useState<string | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } })
  );

  const { data: clusters, isLoading, invalidate } = useClusters(retroId, stepId);

  const { data: me } = useCurrentUser();

  const currentParticipantId = me?.user?.id;

  const invalidateLocal = useCallback(() => {
    invalidate();
  }, [invalidate]);

  useSSESubscription(EventType.NOTE_ADDED, invalidateLocal);
  useSSESubscription(EventType.NOTE_UPDATED, invalidateLocal);
  useSSESubscription(EventType.NOTE_DELETED, invalidateLocal);
  useSSESubscription(EventType.VOTE_ADDED, invalidateLocal);
  useSSESubscription(EventType.VOTE_REMOVED, invalidateLocal);

  const handleVote = async (responseId: string) => {
    await toggleVote(retroId, responseId);
    invalidate();
  };

  const handleEdit = async (responseId: string, newContent: string) => {
    await updateResponse(retroId, responseId, newContent);
    invalidate();
  };

  const handleUnmerge = async (responseId: string) => {
    await unmergeResponse(retroId, stepId, responseId);
    invalidate();
  };

  const handleDragOver = (event: DragOverEvent) => {
    const { over } = event;
    if (over) {
      const overId = String(over.id);
      const isColumn = columns.some((c) => c.id === overId);
      setDragOverColumn(isColumn ? overId : null);
    } else {
      setDragOverColumn(null);
    }
  };

  const handleDragEnd = async (event: DragEndEvent) => {
    setDragOverColumn(null);
    const { active, over } = event;
    if (!over || !allowMerging) return;

    const draggedNoteId = String(active.id);
    const overId = String(over.id);

    if (draggedNoteId === overId) return;

    const isOverColumn = columns.some((c) => c.id === overId);
    if (isOverColumn) return;

    const allNotes = clusters ? allResponses(clusters) : [];
    const draggedNote = allNotes.find((n) => n.id === draggedNoteId);
    const targetNote = allNotes.find((n) => n.id === overId);

    if (!draggedNote || !targetNote) return;
    if (draggedNote.columnId !== targetNote.columnId) return;

    await mergeResponses(retroId, stepId, [draggedNoteId, overId]);
    invalidate();
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-40">
        <div className="w-8 h-8 rounded-full border-4 border-amber-200 border-t-amber-500 animate-spin" />
      </div>
    );
  }

  const allNotes: StickyNoteData[] = clusters
    ? allResponses(clusters).map((r) => ({
        id: r.id ?? "",
        columnId: r.columnId,
        content: r.content,
        visible: showContent || r.visible !== false,
        participantName: r.participantName,
        participantId: r.participantId,
        voteCount: r.voteCount ?? 0,
        clusterId: r.clusterId,
        clusterName: r.clusterName,
      }))
    : [];

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragOver={handleDragOver}
      onDragEnd={(e) => void handleDragEnd(e)}
    >
      <div
        className={[
          "grid gap-4",
          columns.length === 1
            ? "grid-cols-1"
            : columns.length === 2
              ? "grid-cols-2"
              : columns.length === 3
                ? "grid-cols-3"
                : "grid-cols-4",
        ].join(" ")}
      >
        {columns.map((col, colIndex) => {
          const colNotes = allNotes.filter((n) => n.columnId === col.id);
          const noteIds = colNotes.map((n) => n.id);

          return (
            <div key={col.id} data-testid={`column-${col.id}`} data-column={col.title} className="flex flex-col gap-3">
              <div
                className="px-3 py-2 rounded-t-lg font-semibold text-sm text-white"
                style={col.color ? { backgroundColor: col.color } : { backgroundColor: '#6b7280' }}
              >
                {col.title}
                <span className="ml-2 text-xs font-normal opacity-75">({colNotes.length})</span>
              </div>

              <SortableContext items={noteIds} strategy={verticalListSortingStrategy}>
                <ColumnDropZone columnId={col.id}>
                  {colNotes.map((note) => (
                    <StickyNote
                      key={note.id}
                      note={note}
                      currentParticipantId={currentParticipantId}
                      isOwn={note.participantId === currentParticipantId}
                      allowVoting={allowVoting}
                      allowMerging={allowMerging}
                      showVotes={showVotes}
                      showAuthor={showAuthor}
                      retroId={retroId}
                      stepId={stepId}
                      onVote={(id) => void handleVote(id)}
                      onEdit={(id, content) => void handleEdit(id, content)}
                      onUnmerge={(id) => void handleUnmerge(id)}
                    />
                  ))}
                  {colNotes.length === 0 && dragOverColumn !== col.id && (
                    <p className="text-xs text-gray-400 italic text-center py-4">No notes yet</p>
                  )}
                </ColumnDropZone>
              </SortableContext>

              {allowInput && (
                <div
                  className="px-2"
                  {...(colIndex === 0
                    ? { "data-coachmark": "note-input" }
                    : {})}
                >
                  <AddNoteForm
                    columnId={col.id}
                    placeholder={col.placeholder ?? cardConfig.placeholder}
                    maxLength={maxLength}
                    retroId={retroId}
                    stepId={stepId}
                    onSuccess={invalidate}
                  />
                </div>
              )}
            </div>
          );
        })}
      </div>
    </DndContext>
  );
}
