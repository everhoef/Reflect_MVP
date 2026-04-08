import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { Card } from "@/components/ui/card";
import { useActionItems } from "@/hooks/api/useActionItems";
import type { ActionItemDto, CreateActionItemRequest, UpdateActionItemRequest } from "@/hooks/api/useActionItems";
import { useSSESubscription } from "@/hooks/useSSEContext";
import { EventType } from "@/types/events";
import type { StepComponentProps } from "@/components/ComponentRouter";
import { toast } from "sonner";
import { Pencil, Trash2 } from "lucide-react";

// Form Schema
const actionItemSchema = z.object({
  what: z.string().min(1, "What is required"),
  who: z.string().min(1, "Who is required"),
  dueDate: z.string().min(1, "Due Date is required"),
  successCriteria: z.string().optional(),
  escalated: z.boolean().optional(),
});

type ActionItemFormValues = z.infer<typeof actionItemSchema>;

export function SmartActionBuilder({ retroId, componentConfig }: StepComponentProps) {
  const capabilities = componentConfig?.capabilities as Record<string, unknown> | undefined;
  const allowEscalation = Boolean(componentConfig?.allowEscalation || capabilities?.allowEscalation);

  const { data: actionItems, invalidate, createActionItem, updateActionItem, deleteActionItem } = useActionItems(retroId);
  const [editingId, setEditingId] = useState<string | null>(null);

  const form = useForm<ActionItemFormValues>({
    resolver: zodResolver(actionItemSchema),
    defaultValues: {
      what: "",
      who: "",
      dueDate: "",
      successCriteria: "",
      escalated: false,
    },
  });

  const editForm = useForm<ActionItemFormValues>({
    resolver: zodResolver(actionItemSchema),
  });

  // SSE Subscriptions
  useSSESubscription(EventType.ACTION_CREATED, invalidate);
  useSSESubscription(EventType.ACTION_UPDATED, invalidate);
  useSSESubscription(EventType.ACTION_DELETED, invalidate);

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
      // Note: escalated is present in form but omitted from payload as backend doesn't support it yet
      await createActionItem(payload);
      form.reset();
      toast.success("Action item created");
    } catch (e) {
      toast.error("Failed to create action item");
    }
  };

  const onEditSubmit = async (values: ActionItemFormValues) => {
    if (!editingId) return;
    try {
      const payload: UpdateActionItemRequest = {
        what: values.what,
        who: values.who,
        dueDate: values.dueDate,
      };
      if (values.successCriteria) {
        payload.successCriteria = values.successCriteria;
      }
      await updateActionItem({
        actionId: editingId,
        req: payload,
      });
      setEditingId(null);
      toast.success("Action item updated");
    } catch (e) {
      toast.error("Failed to update action item");
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
                    <div className="flex flex-col justify-center gap-1">
                      <label className="flex items-center gap-2 text-sm font-medium text-gray-700 cursor-pointer w-fit" data-testid={`edit-escalation-toggle-label-${item.id}`}>
                        <input
                          type="checkbox"
                          {...editForm.register("escalated")}
                          className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                          data-testid={`edit-escalation-toggle-${item.id}`}
                        />
                        Escalate to Management
                      </label>
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
    </div>
  );
}
