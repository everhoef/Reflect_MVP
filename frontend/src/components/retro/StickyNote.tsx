import { useState } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";

export interface StickyNoteData {
  id: string;
  columnId: string;
  content: string;
  visible: boolean;
  participantName?: string;
  participantId?: string;
  voteCount: number;
  clusterId?: string;
  clusterName?: string;
}

interface StickyNoteProps {
  note: StickyNoteData;
  currentParticipantId?: string;
  isOwn: boolean;
  allowVoting: boolean;
  allowMerging: boolean;
  showVotes: boolean;
  showAuthor: boolean;
  retroId: string;
  stepId: number;
  onVote?: (responseId: string) => void;
  onEdit?: (responseId: string, newContent: string) => void;
  onUnmerge?: (responseId: string) => void;
}

export function StickyNote({
  note,
  isOwn,
  allowVoting,
  showVotes,
  showAuthor,
  onVote,
  onEdit,
  onUnmerge,
}: StickyNoteProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [editContent, setEditContent] = useState(note.content);

  const isHidden = !note.visible && !isOwn;

  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: note.id,
    data: { note },
  });

  // dnd-kit requires transform/transition inline styles to animate drag correctly.
  // CSS.Transform.toString() produces a CSS matrix() string that cannot be expressed
  // as a static Tailwind class — this is the single unavoidable inline style exception.
  const dndStyle: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
  };

  const handleSaveEdit = () => {
    const trimmed = editContent.trim();
    if (trimmed && trimmed !== note.content) {
      onEdit?.(note.id, trimmed);
    }
    setIsEditing(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSaveEdit();
    }
    if (e.key === "Escape") {
      setEditContent(note.content);
      setIsEditing(false);
    }
  };

  return (
    <div ref={setNodeRef} style={dndStyle} className={isDragging ? "opacity-50" : "opacity-100"}>
      <Card
        className={[
          "relative p-3 gap-2 rounded-lg border shadow-sm transition-shadow",
          isDragging ? "shadow-lg ring-2 ring-amber-400" : "hover:shadow-md",
          isOwn ? "border-amber-200 bg-amber-50" : "border-gray-200 bg-white",
        ].join(" ")}
      >
        <div
          {...attributes}
          {...listeners}
          className="absolute top-2 right-2 cursor-grab active:cursor-grabbing p-1 rounded text-gray-300 hover:text-gray-500"
          aria-label="Drag to reorder"
        >
          <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 20 20">
            <path d="M7 4a1 1 0 110-2 1 1 0 010 2zm6 0a1 1 0 110-2 1 1 0 010 2zM7 10a1 1 0 110-2 1 1 0 010 2zm6 0a1 1 0 110-2 1 1 0 010 2zM7 16a1 1 0 110-2 1 1 0 010 2zm6 0a1 1 0 110-2 1 1 0 010 2z" />
          </svg>
        </div>

        {isHidden ? (
          <p className="text-sm text-gray-400 italic select-none blur-sm pr-6">
            {note.content}
          </p>
        ) : isEditing ? (
          <textarea
            autoFocus
            value={editContent}
            onChange={(e) => setEditContent(e.target.value)}
            onBlur={handleSaveEdit}
            onKeyDown={handleKeyDown}
            className="w-full text-sm text-gray-800 resize-none rounded border border-amber-300 bg-white p-1 focus:outline-none focus:ring-2 focus:ring-amber-400 pr-6"
            rows={3}
            maxLength={500}
          />
        ) : (
          <p
            className={[
              "text-sm text-gray-800 pr-6 whitespace-pre-wrap break-words",
              isOwn ? "cursor-pointer" : "",
            ].join(" ")}
            onClick={isOwn ? () => { setIsEditing(true); setEditContent(note.content); } : undefined}
            title={isOwn ? "Click to edit" : undefined}
          >
            {note.content}
          </p>
        )}

        <div className="flex items-center justify-between gap-2 flex-wrap mt-1">
          <div className="flex items-center gap-1.5 flex-wrap">
            {showAuthor && note.participantName && (
              <Badge variant="outline" className="text-xs text-gray-500 py-0 px-1.5">
                {note.participantName}
              </Badge>
            )}
            {note.clusterId && (
              <Badge variant="secondary" className="text-xs py-0 px-1.5">
                {note.clusterName ?? "Cluster"}
                <button
                  onClick={() => onUnmerge?.(note.id)}
                  className="ml-1 text-gray-400 hover:text-red-500"
                  aria-label="Remove from cluster"
                  title="Remove from cluster"
                >
                  ×
                </button>
              </Badge>
            )}
          </div>

          {allowVoting && (
            <button
              onClick={() => onVote?.(note.id)}
              className={[
                "flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium transition-colors",
                "border border-gray-200 hover:border-amber-400 hover:bg-amber-50 hover:text-amber-700",
                "text-gray-500",
              ].join(" ")}
              aria-label={`Vote for this note (${note.voteCount} votes)`}
            >
              👍
              {showVotes && <span>{note.voteCount}</span>}
            </button>
          )}
        </div>
      </Card>
    </div>
  );
}
