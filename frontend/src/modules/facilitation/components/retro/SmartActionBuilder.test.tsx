import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import type { Mock } from "vitest";
import { SmartActionBuilder } from '@/modules/facilitation/components/retro/SmartActionBuilder';
import { useActionItems } from "@/modules/facilitation/hooks/api/useActionItems";
import { useEscalations } from "@/modules/facilitation/hooks/api/useEscalations";
import { useSSESubscription } from "@/shared/hooks/useSSEContext";
import { EventType } from "@/shared/types/events";
import type { ActionItemDto } from "@/modules/facilitation/hooks/api/useActionItems";

// Mock dependencies
vi.mock("@/modules/facilitation/hooks/api/useActionItems", () => ({
  useActionItems: vi.fn(),
}));

vi.mock("@/modules/facilitation/hooks/api/useEscalations", () => ({
  useEscalations: vi.fn(),
}));

vi.mock("@/shared/hooks/useSSEContext", () => ({
  useSSESubscription: vi.fn(),
}));

describe("SmartActionBuilder", () => {
  const mockCreateActionItem = vi.fn();
  const mockUpdateActionItem = vi.fn();
  const mockDeleteActionItem = vi.fn();
  const mockInvalidate = vi.fn();
  const mockEscalateAction = vi.fn();
  const mockToggleVote = vi.fn();
  const mockGetKnownVoteState = vi.fn();
  const mockInvalidateEscalations = vi.fn();
  const mockApplyVoteUpdate = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    (useActionItems as Mock).mockReturnValue({
      data: [] as ActionItemDto[],
      createActionItem: mockCreateActionItem,
      updateActionItem: mockUpdateActionItem,
      deleteActionItem: mockDeleteActionItem,
      invalidate: mockInvalidate,
    });
    
    (useEscalations as Mock).mockReturnValue({
      escalations: [],
      escalateAction: mockEscalateAction,
      toggleVote: mockToggleVote,
      getKnownVoteState: mockGetKnownVoteState,
      invalidate: mockInvalidateEscalations,
      applyVoteUpdate: mockApplyVoteUpdate,
      isVoting: false,
      isEscalating: false,
    });
  });

  it("keeps SSE subscriptions query-driven through shared invalidation callbacks", () => {
    render(<SmartActionBuilder retroId="test-retro" stepId={1} componentConfig={{}} />);

    const subscriptions = (useSSESubscription as Mock).mock.calls;
    const subscribedEventTypes = subscriptions.map(([type]) => type)
    expect(subscribedEventTypes).toEqual(expect.arrayContaining([
      EventType.ACTION_CREATED,
      EventType.ACTION_UPDATED,
      EventType.ACTION_DELETED,
      EventType.ESCALATION_CREATED,
      EventType.ESCALATION_VOTE_UPDATED,
    ]));

    const getHandler = (eventType: EventType) =>
      subscriptions.find(([type]) => type === eventType)?.[1] as ((rawData?: string) => void) | undefined;

    getHandler(EventType.ACTION_CREATED)?.();
    getHandler(EventType.ACTION_UPDATED)?.();
    getHandler(EventType.ACTION_DELETED)?.();
    expect(mockInvalidate).toHaveBeenCalledTimes(3);

    getHandler(EventType.ESCALATION_CREATED)?.();
    expect(mockInvalidate).toHaveBeenCalledTimes(4);
    expect(mockInvalidateEscalations).toHaveBeenCalledTimes(1);

    getHandler(EventType.ESCALATION_VOTE_UPDATED)?.('{"escalationId":"esc-1","voteCount":2,"threshold":2,"thresholdMet":true}');
    expect(mockApplyVoteUpdate).toHaveBeenCalledWith({
      escalationId: 'esc-1',
      voteCount: 2,
      threshold: 2,
      thresholdMet: true,
    });
    expect(mockInvalidateEscalations).toHaveBeenCalledTimes(1);
  });

  it("does not introduce local version-mismatch logic props or reload-style recovery behavior", () => {
    render(<SmartActionBuilder retroId="test-retro" stepId={1} componentConfig={{}} />);

    expect(screen.getByTestId("create-action-btn")).toBeInTheDocument();
    expect((useSSESubscription as Mock).mock.calls.length).toBeGreaterThan(0);

    const renderedText = screen.getByTestId("empty-actions-message").textContent ?? ""
    expect(renderedText).toContain("No action items created yet.")
    expect(renderedText).not.toContain("refresh")
    expect(renderedText).not.toContain("reload")
  });

  it("create with escalation stays mutation/query-driven without reload fallback", async () => {
    mockCreateActionItem.mockResolvedValue({ id: "new-action" });
    mockEscalateAction.mockResolvedValue({ id: "esc-1" });

    render(<SmartActionBuilder retroId="test-retro" stepId={1} componentConfig={{ capabilities: { allowEscalation: true } }} />);

    fireEvent.change(screen.getByTestId("what-input"), { target: { value: "Fix the thing" } });
    fireEvent.change(screen.getByTestId("who-input"), { target: { value: "Alice" } });
    fireEvent.change(screen.getByTestId("due-date-input"), { target: { value: "2026-12-31" } });
    fireEvent.click(screen.getByTestId("escalation-toggle"));
    fireEvent.change(screen.getByTestId("problem-description-input"), { target: { value: "Blocked by org dependency" } });
    fireEvent.click(screen.getByTestId("create-action-btn"));

    await waitFor(() => {
      expect(mockCreateActionItem).toHaveBeenCalledWith({
        what: "Fix the thing",
        who: "Alice",
        dueDate: "2026-12-31",
      });
      expect(mockEscalateAction).toHaveBeenCalledWith({
        actionId: "new-action",
        problemDescription: "Blocked by org dependency",
      });
    });
  });

  it("resets the create form when escalation fails after action creation", async () => {
    mockCreateActionItem.mockResolvedValue({ id: "new-action" });
    mockEscalateAction.mockRejectedValue(new Error("Escalation failed"));

    render(<SmartActionBuilder retroId="test-retro" stepId={1} componentConfig={{ capabilities: { allowEscalation: true } }} />);

    fireEvent.change(screen.getByTestId("what-input"), { target: { value: "Fix the thing" } });
    fireEvent.change(screen.getByTestId("who-input"), { target: { value: "Alice" } });
    fireEvent.change(screen.getByTestId("due-date-input"), { target: { value: "2026-12-31" } });
    fireEvent.click(screen.getByTestId("escalation-toggle"));
    fireEvent.change(screen.getByTestId("problem-description-input"), { target: { value: "Blocked by org dependency" } });
    fireEvent.click(screen.getByTestId("create-action-btn"));

    await waitFor(() => {
      expect(mockCreateActionItem).toHaveBeenCalled();
      expect(mockEscalateAction).toHaveBeenCalled();
      expect(screen.getByTestId("what-input")).toHaveValue("");
      expect(screen.getByTestId("who-input")).toHaveValue("");
      expect(screen.getByTestId("due-date-input")).toHaveValue("");
      expect(screen.getByTestId("escalation-toggle")).not.toBeChecked();
    });
  });

  it("renders the create form and empty list without escalation toggle by default", () => {
    render(<SmartActionBuilder retroId="test-retro" stepId={1} componentConfig={{}} />);
    
    expect(screen.getByText("Create SMART Action")).toBeInTheDocument();
    expect(screen.getByTestId("what-input")).toBeInTheDocument();
    expect(screen.getByTestId("who-input")).toBeInTheDocument();
    expect(screen.getByTestId("due-date-input")).toBeInTheDocument();
    expect(screen.getByTestId("success-criteria-input")).toBeInTheDocument();
    expect(screen.queryByTestId("escalation-toggle")).not.toBeInTheDocument();
    expect(screen.getByText("No action items created yet.")).toBeInTheDocument();
  });

  it("renders the escalation toggle when config allows it", () => {
    render(<SmartActionBuilder retroId="test-retro" stepId={1} componentConfig={{ capabilities: { allowEscalation: true } }} />);
    expect(screen.getByTestId("escalation-toggle")).toBeInTheDocument();
  });

  it("validates required fields", async () => {
    render(<SmartActionBuilder retroId="test-retro" stepId={1} componentConfig={{}} />);
    
    fireEvent.click(screen.getByTestId("create-action-btn"));

    await waitFor(() => {
      expect(screen.getByText("What is required")).toBeInTheDocument();
      expect(screen.getByText("When is required")).toBeInTheDocument();
      expect(screen.getByText("Criteria is required")).toBeInTheDocument();
    });

    expect(mockCreateActionItem).not.toHaveBeenCalled();
  });

  it("submits the form when valid", async () => {
    render(<SmartActionBuilder retroId="test-retro" stepId={1} componentConfig={{}} />);
    
    fireEvent.change(screen.getByTestId("what-input"), { target: { value: "Fix the thing" } });
    fireEvent.change(screen.getByTestId("who-input"), { target: { value: "Alice" } });
    fireEvent.change(screen.getByTestId("due-date-input"), { target: { value: "2026-12-31" } });
    fireEvent.change(screen.getByTestId("success-criteria-input"), { target: { value: "It works" } });

    fireEvent.click(screen.getByTestId("create-action-btn"));

    await waitFor(() => {
      expect(mockCreateActionItem).toHaveBeenCalledWith({
        what: "Fix the thing",
        who: "Alice",
        dueDate: "2026-12-31",
        successCriteria: "It works",
      });
    });
  });

  it("renders action items and handles delete", async () => {
    const mockItem: ActionItemDto = {
      id: "item-1",
      what: "Deploy to prod",
      who: "Bob",
      dueDate: "2026-10-15",
    };

    (useActionItems as Mock).mockReturnValue({
      data: [mockItem],
      createActionItem: mockCreateActionItem,
      updateActionItem: mockUpdateActionItem,
      deleteActionItem: mockDeleteActionItem,
      invalidate: mockInvalidate,
    });

    // Mock confirm
    vi.spyOn(window, "confirm").mockReturnValue(true);

    render(<SmartActionBuilder retroId="test-retro" stepId={1} componentConfig={{}} />);

    expect(screen.getByTestId(`action-what-item-1`)).toHaveTextContent("Deploy to prod");
    
    fireEvent.click(screen.getByTestId(`delete-btn-item-1`));

    await waitFor(() => {
      expect(mockDeleteActionItem).toHaveBeenCalledWith("item-1");
    });
  });

  it("handles edit flow and updates correctly", async () => {
    const mockItem: ActionItemDto = {
      id: "item-2",
      what: "Test editing",
      who: "Charlie",
      dueDate: "2026-11-20",
    };

    (useActionItems as Mock).mockReturnValue({
      data: [mockItem],
      createActionItem: mockCreateActionItem,
      updateActionItem: mockUpdateActionItem,
      deleteActionItem: mockDeleteActionItem,
      invalidate: mockInvalidate,
    });

    render(<SmartActionBuilder retroId="test-retro" stepId={1} componentConfig={{ capabilities: { allowEscalation: true } }} />);

    // Click edit
    fireEvent.click(screen.getByTestId(`edit-btn-item-2`));

    // Verify edit form appears
    expect(screen.getByTestId("edit-what-input")).toHaveValue("Test editing");
    expect(screen.getByTestId("edit-who-input")).toHaveValue("Charlie");
    expect(screen.getByTestId("edit-due-date-input")).toHaveValue("2026-11-20");
    
    // Check escalation toggle in edit mode
    expect(screen.getByTestId(`edit-escalation-toggle-item-2`)).not.toBeChecked();

    // Change value
    fireEvent.change(screen.getByTestId("edit-what-input"), { target: { value: "Test editing updated" } });
    
    // Save
    fireEvent.click(screen.getByTestId("save-edit-btn"));

    await waitFor(() => {
      expect(mockUpdateActionItem).toHaveBeenCalledWith({
        actionId: "item-2",
        req: {
          what: "Test editing updated",
          who: "Charlie",
          dueDate: "2026-11-20",
          successCriteria: "",
        },
      });
    });
  });

  it("closes edit mode when escalation fails after saving the action update", async () => {
    const mockItem: ActionItemDto = {
      id: "item-3",
      what: "Needs escalation",
      who: "Dana",
      dueDate: "2026-11-21",
      escalated: false,
    };

    (useActionItems as Mock).mockReturnValue({
      data: [mockItem],
      createActionItem: mockCreateActionItem,
      updateActionItem: mockUpdateActionItem,
      deleteActionItem: mockDeleteActionItem,
      invalidate: mockInvalidate,
    });
    mockUpdateActionItem.mockResolvedValue({ ...mockItem, what: "Needs escalation updated" });
    mockEscalateAction.mockRejectedValue(new Error("Escalation failed"));

    render(<SmartActionBuilder retroId="test-retro" stepId={1} componentConfig={{ capabilities: { allowEscalation: true } }} />);

    fireEvent.click(screen.getByTestId("edit-btn-item-3"));
    fireEvent.change(screen.getByTestId("edit-what-input"), { target: { value: "Needs escalation updated" } });
    fireEvent.click(screen.getByTestId("edit-escalation-toggle-item-3"));
    fireEvent.change(screen.getByTestId("edit-problem-description-item-3"), { target: { value: "Needs management help" } });
    fireEvent.click(screen.getByTestId("save-edit-btn"));

    await waitFor(() => {
      expect(mockUpdateActionItem).toHaveBeenCalledWith({
        actionId: "item-3",
        req: {
          what: "Needs escalation updated",
          who: "Dana",
          dueDate: "2026-11-21",
          successCriteria: "",
        },
      });
      expect(mockEscalateAction).toHaveBeenCalledWith({
        actionId: "item-3",
        problemDescription: "Needs management help",
      });
      expect(screen.queryByTestId("edit-what-input")).not.toBeInTheDocument();
    });
  });
});
