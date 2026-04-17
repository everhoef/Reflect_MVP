import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { Card } from "@/components/ui/card";
import { useActionItems } from "@/hooks/api/useActionItems";
import { useEscalations } from "@/hooks/api/useEscalations";
import type { ActionItemDto, CreateActionItemRequest, UpdateActionItemRequest } from "@/hooks/api/useActionItems";
import { useSSESubscription } from "@/hooks/useSSEContext";
import { EventType } from "@/types/events";
import type { StepComponentProps } from "@/components/ComponentRouter";
import { toast } from "sonner";
import { Pencil, Trash2, AlertCircle } from "lucide-react";
import { EscalationVote } from "./EscalationVote";

// Form Schema
const baseActionItemSchema = z.object({
  what: z.string().min(1, "What is required"),
  who: z.string().min(1, "Who is required"),
  dueDate: z.string().min(1, "Due Date is required"),
  successCriteria: z.string().optional(),
  escalated: z.boolean().optional(),
  wasEscalated: z.boolean().optional(),
  problemDescription: z.string().optional(),
});

const actionItemSchema = baseActionItemSchema.refine(
  data => !data.escalated || data.wasEscalated || (data.escalated && data.problemDescription && data.problemDescription.length > 0), 
  {
    message: "Problem description is required when escalating",
    path: ["problemDescription"]
  }
);

type ActionItemFormValues = z.infer<typeof actionItemSchema>;

function getErrorMessage(error: unknown, fallbackMessage: string): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }

  return fallbackMessage;
}

export function SmartActionBuilder({ retroId, componentConfig }: StepComponentProps) {
  const capabilities = componentConfig?.capabilities as Record<string, unknown> | undefined;
  const allowEscalation = Boolean(componentConfig?.allowEscalation || capabilities?.allowEscalation);

  const { data: actionItems, invalidate: invalidateActions, createActionItem, updateActionItem, deleteActionItem } = useActionItems(retroId);
  const { escalations, escalateAction, toggleVote, isVoting, getKnownVoteState, invalidate: invalidateEscalations, applyVoteUpdate } = useEscalations(retroId);
  const [editingId, setEditingId] = useState<string | null>(null);

  const invalidateEscalationLiveState = () => {
    invalidateActions();
    invalidateEscalations();
  };

  const form = useForm<ActionItemFormValues>({
    resolver: zodResolver(actionItemSchema),
    defaultValues: {
      what: "",
      who: "",
      dueDate: "",
      successCriteria: "",
      escalated: false,
      wasEscalated: false,
      problemDescription: "",
    },
  });

  const isEscalated = useWatch({
    control: form.control,
    name: "escalated",
  });

  const editForm = useForm<ActionItemFormValues>({
    resolver: zodResolver(actionItemSchema),
  });

  // SSE Subscriptions
  useSSESubscription(EventType.ACTION_CREATED, invalidateActions);
  useSSESubscription(EventType.ACTION_UPDATED, invalidateActions);
  useSSESubscription(EventType.ACTION_DELETED, invalidateActions);
  useSSESubscription(EventType.ESCALATION_CREATED, invalidateEscalationLiveState);
  useSSESubscription(EventType.ESCALATION_VOTE_UPDATED, (rawData) => {
    const parsed = JSON.parse(rawData) as {
      escalationId?: string;
      voteCount?: number;
      threshold?: number;
      thresholdMet?: boolean;
    };
    applyVoteUpdate(parsed);
  });

  const onSubmit = async (values: ActionItemFormValues) => {
    try {
      const payload: CreateActionItemRequest = {
        what: values.what,
        who: values.who,
        dueDate: values.dueDate,
      };
      if (values.successCriteria) {
        payload.successCriteria = values.successCriteria;
      }
      
      const createdItem = await createActionItem(payload);
      
      if (values.escalated && values.problemDescription && createdItem?.id) {
        try {
          await escalateAction({
            actionId: createdItem.id as string,
            problemDescription: values.problemDescription
          });
        } catch (escalateErr: unknown) {
          form.reset();
          toast.error(getErrorMessage(escalateErr, "Action created, but failed to escalate"));
          return;
        }
      }
      
      form.reset();
      toast.success("Action item created");
    } catch (error: unknown) {
      toast.error(getErrorMessage(error, "Failed to create action item"));
    }
  };

  const onEditSubmit = async (values: ActionItemFormValues) => {
    if (!editingId) return;
    try {
      const payload: UpdateActionItemRequest = {
        what: values.what,
        who: values.who,
        dueDate: values.dueDate,
        successCriteria: values.successCriteria ?? "",
      };
      await updateActionItem({
        actionId: editingId,
        req: payload,
      });

      const itemWasAlreadyEscalated = actionItems?.find(a => a.id === editingId)?.escalated;
      if (values.escalated && !itemWasAlreadyEscalated && values.problemDescription) {
        try {
          await escalateAction({
            actionId: editingId,
            problemDescription: values.problemDescription
          });
        } catch (escalateErr: unknown) {
          setEditingId(null);
          editForm.reset();
          toast.error(getErrorMessage(escalateErr, "Action updated, but failed to escalate"));
          return;
        }
      }

      setEditingId(null);
      toast.success("Action item updated");
    } catch (error: unknown) {
      toast.error(getErrorMessage(error, "Failed to update action item"));
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Are you sure you want to delete this action item?")) return;
    try {
      await deleteActionItem(id);
      toast.success("Action item deleted");
    } catch (e) {
      toast.error("Failed to delete action item");
    }
  };

  const startEdit = (item: ActionItemDto) => {
    if (!item.id) return;
    setEditingId(item.id);
    editForm.reset({
      what: item.what || "",
      who: item.who || "",
      dueDate: item.dueDate || "",
      successCriteria: item.successCriteria || "",
      escalated: item.escalated || false,
      wasEscalated: item.escalated || false,
      problemDescription: "",
    });
  };

  const cancelEdit = () => {
    setEditingId(null);
    editForm.reset();
  };

  return (
    <div className="flex flex-col gap-6 w-full max-w-4xl mx-auto">
      {/* Create Form */}
      <Card className="p-6 bg-white shadow-sm border-gray-200">
        <h2 className="text-xl font-semibold mb-4">Create SMART Action</h2>
        <form onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="flex flex-col gap-1">
              <label className="text-sm font-medium text-gray-700">What <span className="text-red-500">*</span></label>
              <input 
                {...form.register("what")} 
                className="flex h-10 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" 
                placeholder="What exactly?" 
                data-testid="what-input"
              />
              {form.formState.errors.what && <p className="text-xs text-red-500">{form.formState.errors.what.message}</p>}
            </div>

            <div className="flex flex-col gap-1">
              <label className="text-sm font-medium text-gray-700">Who <span className="text-red-500">*</span></label>
              <input 
                {...form.register("who")} 
                className="flex h-10 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" 
                placeholder="Who owns this?" 
                data-testid="who-input"
              />
              {form.formState.errors.who && <p className="text-xs text-red-500">{form.formState.errors.who.message}</p>}
            </div>

            <div className="flex flex-col gap-1">
              <label className="text-sm font-medium text-gray-700">Due Date <span className="text-red-500">*</span></label>
              <input 
                type="date" 
                {...form.register("dueDate")} 
                className="flex h-10 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" 
                data-testid="due-date-input"
              />
              {form.formState.errors.dueDate && <p className="text-xs text-red-500">{form.formState.errors.dueDate.message}</p>}
            </div>

            <div className="flex flex-col gap-1">
              <label className="text-sm font-medium text-gray-700">Success Criteria</label>
              <input 
                {...form.register("successCriteria")} 
                className="flex h-10 w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" 
                placeholder="How will we know it's done?" 
                data-testid="success-criteria-input"
              />
            </div>
            
            {allowEscalation && (
              <div className="flex flex-col gap-2">
                <div className="flex flex-col justify-center gap-1">
                  <label className="flex items-center gap-2 text-sm font-medium text-gray-700 cursor-pointer w-fit" data-testid="escalation-toggle-label">
                    <input
                      type="checkbox"
                      {...form.register("escalated")}
                      className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                      data-testid="escalation-toggle"
                    />
                    Escalate to Management
                  </label>
                  <p className="text-xs text-gray-500">Flag this item as an organizational bottleneck.</p>
                </div>
                
                {isEscalated && (
                  <div className="flex flex-col gap-1 mt-2">
                    <label className="text-sm font-medium text-gray-700">Problem Description <span className="text-red-500">*</span></label>
                    <textarea 
                      {...form.register("problemDescription")} 
                      className="flex min-h-[80px] w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" 
                      placeholder="Why does this need management attention? What is the core problem?" 
                      data-testid="problem-description-input"
                    />
                    {form.formState.errors.problemDescription && <p className="text-xs text-red-500">{form.formState.errors.problemDescription.message}</p>}
                  </div>
                )}
              </div>
            )}
          </div>
          
          <button 
            type="submit" 
            disabled={form.formState.isSubmitting}
            className="self-end mt-2 inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:pointer-events-none disabled:opacity-50 bg-blue-600 text-white hover:bg-blue-700 h-10 px-4 py-2"
            data-testid="create-action-btn"
          >
            Add Action Item
          </button>
        </form>
      </Card>

      {/* Action Items List */}
      <div className="flex flex-col gap-3">
        <h3 className="text-lg font-medium text-gray-800">Action Items</h3>
        
        {(!actionItems || actionItems.length === 0) && (
          <div className="text-sm text-gray-500 italic p-4 bg-gray-50 rounded-md border border-dashed border-gray-300 text-center" data-testid="empty-actions-message">
            No action items created yet.
          </div>
        )}

        {actionItems?.map((item) => (
          <Card key={item.id} className="p-4 bg-white shadow-sm border-gray-200">
            {editingId === item.id ? (
              <form onSubmit={editForm.handleSubmit(onEditSubmit)} className="flex flex-col gap-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="flex flex-col gap-1">
                    <label className="text-sm font-medium text-gray-700">What</label>
                    <input {...editForm.register("what")} className="flex h-10 w-full rounded-md border border-gray-300 px-3 py-2 text-sm" data-testid="edit-what-input" />
                    {editForm.formState.errors.what && <p className="text-xs text-red-500">{editForm.formState.errors.what.message}</p>}
                  </div>
                  <div className="flex flex-col gap-1">
                    <label className="text-sm font-medium text-gray-700">Who</label>
                    <input {...editForm.register("who")} className="flex h-10 w-full rounded-md border border-gray-300 px-3 py-2 text-sm" data-testid="edit-who-input" />
                    {editForm.formState.errors.who && <p className="text-xs text-red-500">{editForm.formState.errors.who.message}</p>}
                  </div>
                  <div className="flex flex-col gap-1">
                    <label className="text-sm font-medium text-gray-700">Due Date</label>
                    <input type="date" {...editForm.register("dueDate")} className="flex h-10 w-full rounded-md border border-gray-300 px-3 py-2 text-sm" data-testid="edit-due-date-input" />
                    {editForm.formState.errors.dueDate && <p className="text-xs text-red-500">{editForm.formState.errors.dueDate.message}</p>}
                  </div>
                  <div className="flex flex-col gap-1">
                    <label className="text-sm font-medium text-gray-700">Success Criteria</label>
                    <input {...editForm.register("successCriteria")} className="flex h-10 w-full rounded-md border border-gray-300 px-3 py-2 text-sm" data-testid="edit-success-criteria-input" />
                  </div>
                  
                  {allowEscalation && (
                    <div className="flex flex-col gap-2">
                      <div className="flex flex-col justify-center gap-1">
                        <label className={`flex items-center gap-2 text-sm font-medium cursor-pointer w-fit ${item.escalated ? 'text-gray-400' : 'text-gray-700'}`} data-testid={`edit-escalation-toggle-label-${item.id}`}>
                          <input
                            type="checkbox"
                            {...editForm.register("escalated")}
                            disabled={item.escalated}
                            className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500 disabled:opacity-50"
                            data-testid={`edit-escalation-toggle-${item.id}`}
                          />
                          {item.escalated ? "Already Escalated" : "Escalate to Management"}
                        </label>
                      </div>
                      
                      {!item.escalated && editForm.watch("escalated") && (
                        <div className="flex flex-col gap-1 mt-1">
                          <label className="text-sm font-medium text-gray-700">Problem Description <span className="text-red-500">*</span></label>
                          <textarea 
                            {...editForm.register("problemDescription")} 
                            className="flex min-h-[80px] w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-sm placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent" 
                            placeholder="Why does this need management attention? What is the core problem?" 
                            data-testid={`edit-problem-description-${item.id}`}
                          />
                          {editForm.formState.errors.problemDescription && <p className="text-xs text-red-500">{editForm.formState.errors.problemDescription.message}</p>}
                        </div>
                      )}
                    </div>
                  )}
                </div>
                <div className="flex justify-end gap-2 mt-2">
                  <button type="button" onClick={cancelEdit} className="inline-flex h-10 items-center justify-center rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50" data-testid="cancel-edit-btn">Cancel</button>
                  <button type="submit" disabled={editForm.formState.isSubmitting} className="inline-flex h-10 items-center justify-center rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700" data-testid="save-edit-btn">Save</button>
                </div>
              </form>
            ) : (
              <div className="flex justify-between items-start gap-4">
                <div className="flex flex-col gap-2 flex-grow">
                  <div className="flex items-center gap-2">
                    <div className="font-medium text-gray-900 text-lg" data-testid={`action-what-${item.id}`}>{item.what}</div>
                    {item.escalated && (
                      <span className="inline-flex items-center rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-semibold text-red-800" data-testid={`action-escalated-badge-${item.id}`}>
                        Escalated
                      </span>
                    )}
                  </div>
                  
                  <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm text-gray-600">
                    <div className="flex items-center gap-1">
                      <span className="font-semibold text-gray-700">Who:</span>
                      <span data-testid={`action-who-${item.id}`}>{item.who}</span>
                    </div>
                    <div className="flex items-center gap-1">
                      <span className="font-semibold text-gray-700">Due:</span>
                      <span data-testid={`action-due-${item.id}`}>{item.dueDate}</span>
                    </div>
                    {item.successCriteria && (
                      <div className="flex items-center gap-1 w-full">
                        <span className="font-semibold text-gray-700">Success:</span>
                        <span data-testid={`action-success-${item.id}`}>{item.successCriteria}</span>
                      </div>
                    )}
                  </div>
                  
                  {item.escalated && (
                    <div className="mt-2 text-sm text-red-600 flex items-center gap-1">
                      <AlertCircle className="w-4 h-4" />
                      <span>This action item has been escalated.</span>
                    </div>
                  )}
                </div>
                
                <div className="flex gap-2 shrink-0">
                  <button 
                    onClick={() => startEdit(item)}
                    className="p-2 text-gray-500 hover:text-blue-600 hover:bg-blue-50 rounded-md transition-colors"
                    aria-label="Edit"
                    data-testid={`edit-btn-${item.id}`}
                  >
                    <Pencil size={18} />
                  </button>
                  <button 
                    onClick={() => item.id && handleDelete(item.id)}
                    className="p-2 text-gray-500 hover:text-red-600 hover:bg-red-50 rounded-md transition-colors"
                    aria-label="Delete"
                    data-testid={`delete-btn-${item.id}`}
                  >
                    <Trash2 size={18} />
                  </button>
                </div>
              </div>
            )}
          </Card>
        ))}
      </div>

      {/* Escalations List */}
      {allowEscalation && escalations && escalations.length > 0 && (
        <div className="flex flex-col gap-3 mt-4 w-full">
          <h3 className="text-lg font-medium text-gray-800">Management Escalations</h3>
          <div className="flex flex-col gap-2">
            {escalations.map(esc => (
              <EscalationVote 
                key={esc.id} 
                escalation={esc} 
                voted={esc.id ? getKnownVoteState(esc.id) : undefined}
                isVoting={isVoting}
                onToggleVote={() => {
                  if (esc.id) {
                    toggleVote(esc.id).catch(() => {
                      toast.error("Failed to toggle vote");
                    });
                  }
                }}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
