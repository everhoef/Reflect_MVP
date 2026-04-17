import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { components } from '@/types/api.d.ts';
import { AlertCircle, ArrowUpCircle, CheckCircle2 } from 'lucide-react';

type EscalatedItemDto = components['schemas']['EscalatedItemDto'];

interface EscalationVoteProps {
  escalation: EscalatedItemDto;
  voted?: boolean | undefined;
  isVoting?: boolean;
  onToggleVote?: () => void | Promise<void>;
}

export function EscalationVote({ escalation, voted, isVoting, onToggleVote }: EscalationVoteProps) {
  const handleVote = () => {
    if (!escalation.id || !onToggleVote) return;
    void onToggleVote();
  };

  const voteCount = escalation.voteCount ?? 0;
  const threshold = escalation.threshold ?? 2;
  const progressPercent = Math.min((voteCount / threshold) * 100, 100);

  const getButtonLabel = () => {
    if (voted === true) return `Unvote (${voteCount})`;
    if (voted === false) return `Vote (${voteCount})`;
    return `Toggle Vote (${voteCount})`;
  };

  return (
    <div className="mt-3 p-3 bg-red-50/50 border border-red-100 rounded-md">
      <div className="flex items-start justify-between">
        <div className="flex-1 mr-4">
          <div className="flex items-center gap-2 mb-1">
            <AlertCircle className="w-4 h-4 text-red-500" />
            <span className="text-sm font-semibold text-red-700">Escalated to Management</span>
            {escalation.thresholdMet && (
              <Badge variant="destructive" className="ml-2 text-[10px] h-5">Threshold Met</Badge>
            )}
          </div>
          <p className="text-sm text-gray-700 italic">"{escalation.problemDescription}"</p>
        </div>
        
        <div className="flex flex-col items-end min-w-[120px]">
          <Button
            size="sm"
            variant={voted ? "default" : "outline"}
            className={`w-full ${voted ? 'bg-red-600 hover:bg-red-700 text-white' : 'text-red-600 border-red-200 hover:bg-red-50'}`}
            onClick={handleVote}
            disabled={isVoting}
          >
            {voted ? <CheckCircle2 className="w-4 h-4 mr-2" /> : <ArrowUpCircle className="w-4 h-4 mr-2" />}
            {getButtonLabel()}
          </Button>
          
          <div className="w-full mt-2">
            <div className="flex justify-between text-[10px] text-gray-500 mb-1">
              <span>Votes</span>
              <span>{voteCount} / {threshold}</span>
            </div>
            <div className="h-1.5 w-full bg-gray-100 overflow-hidden rounded-full">
              <div className="h-full bg-red-500" style={{ width: `${progressPercent}%` }} />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
