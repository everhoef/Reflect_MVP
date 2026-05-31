import { Card } from "@/shared/ui/card";
import { useActionItems } from "@/modules/facilitation/hooks/api/useActionItems";
import type { StepComponentProps } from "@/modules/facilitation/components/ComponentRouter";

export function ActionReview({ retroId, componentConfig }: StepComponentProps) {
  const { data: actionItems, isLoading, error } = useActionItems(retroId);
  const reviewedActionItems = actionItems ?? [];
  
  // Component Config Contract
  const showStatus = componentConfig?.showStatus !== false;
  const emptyStateMessage = (componentConfig?.emptyStateMessage as string) || "No action items created in this retrospective yet.";

  if (isLoading) {
    return (
      <div className="flex flex-col gap-6 w-full max-w-4xl mx-auto" data-testid="action-review-loading">
        <div className="flex items-center justify-center h-40">
          <div className="w-8 h-8 rounded-full border-4 border-amber-200 border-t-amber-500 animate-spin" />
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col gap-6 w-full max-w-4xl mx-auto" data-testid="action-review-error">
        <div className="text-center p-8 bg-red-50 text-red-600 rounded-lg border border-red-200">
          Failed to load action items.
        </div>
      </div>
    );
  }

  const hasItems = reviewedActionItems.length > 0;

  return (
    <div className="flex flex-col gap-6 w-full max-w-4xl mx-auto" data-testid="action-review-container">
      <div className="flex justify-between items-center mb-2">
        <h2 className="text-xl font-semibold text-gray-800">Action Review</h2>
      </div>

      {!hasItems && (
        <div className="text-sm text-gray-500 italic p-8 bg-gray-50 rounded-md border border-dashed border-gray-300 text-center" data-testid="empty-actions-message">
          {emptyStateMessage}
        </div>
      )}

      {hasItems && (
        <div className="flex flex-col gap-3">
          {reviewedActionItems.map((item) => {
            const isDone = item.status === "DONE";
            
            return (
              <Card key={item.id} className={`p-4 shadow-sm border-gray-200 transition-colors ${isDone ? 'bg-gray-50 opacity-75' : 'bg-white'}`} data-testid={`action-card-${item.id}`}>
                <div className="flex justify-between items-start gap-4">
                  <div className="flex flex-col gap-2 flex-grow">
                    <div className="flex items-center gap-2">
                      <div className={`font-medium text-lg ${isDone ? 'text-gray-500 line-through' : 'text-gray-900'}`} data-testid={`action-what-${item.id}`}>
                        {item.what}
                      </div>
                      {item.escalated && (
                        <span className="inline-flex items-center rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-semibold text-red-800" data-testid={`action-escalated-badge-${item.id}`}>
                          Escalated
                        </span>
                      )}
                      {showStatus && isDone && (
                        <span className="inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-semibold text-green-800" data-testid={`action-done-badge-${item.id}`}>
                          Done
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
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
