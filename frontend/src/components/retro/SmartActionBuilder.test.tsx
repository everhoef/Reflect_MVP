import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import type { Mock } from "vitest";
import { SmartActionBuilder } from "./SmartActionBuilder";
import { useActionItems } from "@/hooks/api/useActionItems";
import type { ActionItemDto } from "@/hooks/api/useActionItems";

// Mock dependencies
vi.mock("@/hooks/api/useActionItems", () => ({
  useActionItems: vi.fn(),
}));

vi.mock("@/hooks/useSSEContext", () => ({
  useSSESubscription: vi.fn(),
}));

describe("SmartActionBuilder", () => {
  const mockCreateActionItem = vi.fn();
  const mockUpdateActionItem = vi.fn();
  const mockDeleteActionItem = vi.fn();
  const mockInvalidate = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    (useActionItems as Mock).mockReturnValue({
      data: [] as ActionItemDto[],
      createActionItem: mockCreateActionItem,
      updateActionItem: mockUpdateActionItem,
      deleteActionItem: mockDeleteActionItem,
      invalidate: mockInvalidate,
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
      expect(screen.getByText("Who is required")).toBeInTheDocument();
      expect(screen.getByText("Due Date is required")).toBeInTheDocument();
    });

    expect(mockCreateActionItem).not.toHaveBeenCalled();
  });

  it("submits the form when valid", async () => {
    render(<SmartActionBuilder retroId="test-retro" stepId={1} componentConfig={{}} />);
    
    fireEvent.change(screen.getByTestId("what-input"), { target: { value: "Fix the thing" } });
    fireEvent.change(screen.getByTestId("who-input"), { target: { value: "Alice" } });
    fireEvent.change(screen.getByTestId("due-date-input"), { target: { value: "2026-12-31" } });
    
    fireEvent.click(screen.getByTestId("create-action-btn"));

    await waitFor(() => {
      expect(mockCreateActionItem).toHaveBeenCalledWith({
        what: "Fix the thing",
        who: "Alice",
        dueDate: "2026-12-31",
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
        },
      });
    });
  });
});
