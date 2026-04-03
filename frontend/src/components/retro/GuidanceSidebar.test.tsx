import { render, screen } from '@testing-library/react'
import { GuidanceSidebar } from './GuidanceSidebar'
import type { HistoryMessage, PrivateCoachingNote, FacilitatorQuickActionsConfig } from './GuidanceSidebar'

describe('GuidanceSidebar', () => {
  it('renders the default title "Now" when no stepTitle provided', () => {
    render(<GuidanceSidebar />)
    expect(screen.getByText('Now')).toBeInTheDocument()
  })

  it('renders a custom stepTitle when provided', () => {
    render(<GuidanceSidebar stepTitle="Mad Sad Glad" />)
    expect(screen.getByText('Mad Sad Glad')).toBeInTheDocument()
  })

  it('renders guidance text when guidance is provided', () => {
    render(<GuidanceSidebar guidance="Ask participants to reflect on the sprint." />)
    expect(screen.getByText('Ask participants to reflect on the sprint.')).toBeInTheDocument()
  })

  it('renders "No guidance available" message when guidance is null', () => {
    render(<GuidanceSidebar guidance={null} />)
    expect(screen.getByText('No guidance available for this step.')).toBeInTheDocument()
  })

  it('renders "No guidance available" message when guidance is undefined', () => {
    render(<GuidanceSidebar />)
    expect(screen.getByText('No guidance available for this step.')).toBeInTheDocument()
  })

  it('renders "No guidance available" message when guidance is empty string', () => {
    render(<GuidanceSidebar guidance="" />)
    expect(screen.getByText('No guidance available for this step.')).toBeInTheDocument()
  })

  it('renders bullet points for lines starting with dash', () => {
    render(<GuidanceSidebar guidance={`- First step\n- Second step`} />)
    expect(screen.getByText('First step')).toBeInTheDocument()
    expect(screen.getByText('Second step')).toBeInTheDocument()
  })

  it('renders bullet points for lines starting with bullet character', () => {
    render(<GuidanceSidebar guidance={`• First step\n• Second step`} />)
    expect(screen.getByText('First step')).toBeInTheDocument()
    expect(screen.getByText('Second step')).toBeInTheDocument()
  })

  it('renders plain paragraph for non-bullet lines', () => {
    render(<GuidanceSidebar guidance="Plain text line" />)
    expect(screen.getByText('Plain text line')).toBeInTheDocument()
  })

  it('is always visible — cannot be collapsed (BDD Scenario 11)', () => {
    render(<GuidanceSidebar guidance="Some guidance text" />)
    expect(screen.getByText('Some guidance text')).toBeVisible()
  })

  it('has no collapse or expand button (BDD Scenario 11)', () => {
    render(<GuidanceSidebar guidance="Some guidance text" />)
    expect(screen.queryByRole('button', { name: /collapse guidance/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /expand guidance/i })).not.toBeInTheDocument()
    expect(screen.queryByText('Show guidance ↓')).not.toBeInTheDocument()
  })

  describe('stable selector hook contract', () => {
    it('renders the guidance-sidebar shell', () => {
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('guidance-sidebar')).toBeInTheDocument()
    })

    it('guidance-sidebar has data-assistant-testid="assistant-shell"', () => {
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('guidance-sidebar')).toHaveAttribute('data-assistant-testid', 'assistant-shell')
    })

    it('guidance-sidebar has data-coachmark="guidance-sidebar"', () => {
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('guidance-sidebar')).toHaveAttribute('data-coachmark', 'guidance-sidebar')
    })

    it('guidance-content is always in DOM when component renders', () => {
      render(<GuidanceSidebar guidance="Some guidance text" />)
      expect(screen.getByTestId('guidance-content')).toBeInTheDocument()
    })

    it('guidance-content is present even when no guidance is provided', () => {
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('guidance-content')).toBeInTheDocument()
    })

    it('guidance-content has data-assistant-testid="assistant-current-message"', () => {
      render(<GuidanceSidebar guidance="Some guidance text" />)
      expect(screen.getByTestId('guidance-content')).toHaveAttribute('data-assistant-testid', 'assistant-current-message')
    })

    it('assistant-history-list is always in DOM', () => {
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('assistant-history-list')).toBeInTheDocument()
    })

    it('assistant-history-list has aria-hidden="true" when no history', () => {
      render(<GuidanceSidebar previousMessages={[]} />)
      expect(screen.getByTestId('assistant-history-list')).toHaveAttribute('aria-hidden', 'true')
    })

    it('assistant-history-list does not have aria-hidden when history is present', () => {
      const messages: HistoryMessage[] = [{ stepTitle: 'Step 1', publicText: 'Some text' }]
      render(<GuidanceSidebar previousMessages={messages} />)
      expect(screen.getByTestId('assistant-history-list')).not.toHaveAttribute('aria-hidden', 'true')
    })

    it('assistant-private-coaching-placeholder is in DOM when private note not shown', () => {
      render(<GuidanceSidebar isFacilitator={false} />)
      expect(screen.getByTestId('assistant-private-coaching-placeholder')).toBeInTheDocument()
    })

    it('assistant-private-coaching-placeholder has aria-hidden="true" and hidden when not shown', () => {
      render(<GuidanceSidebar isFacilitator={false} />)
      const placeholder = screen.getByTestId('assistant-private-coaching-placeholder')
      expect(placeholder).toHaveAttribute('aria-hidden', 'true')
      expect(placeholder).toHaveAttribute('hidden')
    })

    it('facilitator-quick-actions-placeholder is in DOM when quick actions not shown', () => {
      render(<GuidanceSidebar isFacilitator={false} />)
      expect(screen.getByTestId('facilitator-quick-actions-placeholder')).toBeInTheDocument()
    })

    it('facilitator-quick-actions-placeholder has aria-hidden="true" and hidden when not shown', () => {
      render(<GuidanceSidebar isFacilitator={false} />)
      const placeholder = screen.getByTestId('facilitator-quick-actions-placeholder')
      expect(placeholder).toHaveAttribute('aria-hidden', 'true')
      expect(placeholder).toHaveAttribute('hidden')
    })
  })

  describe('facilitator-private coaching boundary', () => {
    const note: PrivateCoachingNote = { text: 'Remind them to stay on topic.' }

    it('does not render private coaching section for non-facilitator', () => {
      render(<GuidanceSidebar isFacilitator={false} privateCoachingNote={note} />)
      expect(screen.queryByTestId('assistant-private-coaching')).not.toBeInTheDocument()
    })

    it('does not render private coaching section when note is null', () => {
      render(<GuidanceSidebar isFacilitator={true} privateCoachingNote={null} />)
      expect(screen.queryByTestId('assistant-private-coaching')).not.toBeInTheDocument()
    })

    it('does not render private coaching section when note text is empty', () => {
      render(<GuidanceSidebar isFacilitator={true} privateCoachingNote={{ text: '' }} />)
      expect(screen.queryByTestId('assistant-private-coaching')).not.toBeInTheDocument()
    })

    it('renders private coaching section for facilitator with valid note', () => {
      render(<GuidanceSidebar isFacilitator={true} privateCoachingNote={note} />)
      expect(screen.getByTestId('assistant-private-coaching')).toBeInTheDocument()
    })

    it('renders private coaching note text', () => {
      render(<GuidanceSidebar isFacilitator={true} privateCoachingNote={note} />)
      expect(screen.getByText('Remind them to stay on topic.')).toBeInTheDocument()
    })

    it('private coaching section has data-assistant-testid="assistant-private-coaching"', () => {
      render(<GuidanceSidebar isFacilitator={true} privateCoachingNote={note} />)
      expect(screen.getByTestId('assistant-private-coaching')).toHaveAttribute('data-assistant-testid', 'assistant-private-coaching')
    })

    it('shows placeholder instead of section when coaching is absent', () => {
      render(<GuidanceSidebar isFacilitator={false} />)
      expect(screen.queryByTestId('assistant-private-coaching')).not.toBeInTheDocument()
      expect(screen.getByTestId('assistant-private-coaching-placeholder')).toBeInTheDocument()
    })

    it('placeholder has data-assistant-testid="assistant-private-coaching"', () => {
      render(<GuidanceSidebar isFacilitator={false} />)
      expect(screen.getByTestId('assistant-private-coaching-placeholder')).toHaveAttribute('data-assistant-testid', 'assistant-private-coaching')
    })
  })

  describe('shared assistant history rendering', () => {
    const messages: HistoryMessage[] = [
      { stepTitle: 'Step A', publicText: 'First message' },
      { stepTitle: 'Step B', publicText: 'Second message' },
    ]

    it('history container is always in DOM regardless of expand state', () => {
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('assistant-history-list')).toBeInTheDocument()
    })

    it('renders history messages when provided', () => {
      render(<GuidanceSidebar previousMessages={messages} />)
      expect(screen.getByText('First message')).toBeInTheDocument()
      expect(screen.getByText('Second message')).toBeInTheDocument()
    })

    it('renders step titles for history messages', () => {
      render(<GuidanceSidebar previousMessages={messages} />)
      expect(screen.getByText('Step A')).toBeInTheDocument()
      expect(screen.getByText('Step B')).toBeInTheDocument()
    })

    it('caps rendered history at 3 messages (HISTORY_RENDER_MAX)', () => {
      const fourMessages: HistoryMessage[] = [
        { stepTitle: 'Step 1', publicText: 'Msg 1' },
        { stepTitle: 'Step 2', publicText: 'Msg 2' },
        { stepTitle: 'Step 3', publicText: 'Msg 3' },
        { stepTitle: 'Step 4', publicText: 'Msg 4 — should be hidden' },
      ]
      render(<GuidanceSidebar previousMessages={fourMessages} />)
      expect(screen.getByText('Msg 1')).toBeInTheDocument()
      expect(screen.getByText('Msg 2')).toBeInTheDocument()
      expect(screen.getByText('Msg 3')).toBeInTheDocument()
      expect(screen.queryByText('Msg 4 — should be hidden')).not.toBeInTheDocument()
    })

    it('shows "Earlier" label when history messages exist', () => {
      render(<GuidanceSidebar previousMessages={messages} />)
      expect(screen.getByText('Earlier')).toBeInTheDocument()
    })

    it('does not show "Earlier" label when no history', () => {
      render(<GuidanceSidebar previousMessages={[]} />)
      expect(screen.queryByText('Earlier')).not.toBeInTheDocument()
    })

    it('renders empty history container without errors', () => {
      render(<GuidanceSidebar previousMessages={[]} />)
      expect(screen.getByTestId('assistant-history-list')).toBeInTheDocument()
    })
  })

  describe('facilitator quick actions', () => {
    it('does not render quick actions section for non-facilitator', () => {
      const quickActions: FacilitatorQuickActionsConfig = { onAdvanceStep: vi.fn() }
      render(<GuidanceSidebar isFacilitator={false} quickActions={quickActions} />)
      expect(screen.queryByTestId('facilitator-quick-actions')).not.toBeInTheDocument()
    })

    it('does not render quick actions section when quickActions is null', () => {
      render(<GuidanceSidebar isFacilitator={true} quickActions={null} />)
      expect(screen.queryByTestId('facilitator-quick-actions')).not.toBeInTheDocument()
    })

    it('does not render quick actions section when onAdvanceStep is undefined', () => {
      render(<GuidanceSidebar isFacilitator={true} quickActions={{}} />)
      expect(screen.queryByTestId('facilitator-quick-actions')).not.toBeInTheDocument()
    })

    it('renders quick actions section for facilitator with onAdvanceStep', () => {
      const quickActions: FacilitatorQuickActionsConfig = { onAdvanceStep: vi.fn() }
      render(<GuidanceSidebar isFacilitator={true} quickActions={quickActions} />)
      expect(screen.getByTestId('facilitator-quick-actions')).toBeInTheDocument()
    })

    it('renders advance step button when onAdvanceStep is provided', () => {
      const quickActions: FacilitatorQuickActionsConfig = { onAdvanceStep: vi.fn() }
      render(<GuidanceSidebar isFacilitator={true} quickActions={quickActions} />)
      expect(screen.getByTestId('quick-action-advance-step')).toBeInTheDocument()
    })

    it('advance step button has correct text', () => {
      const quickActions: FacilitatorQuickActionsConfig = { onAdvanceStep: vi.fn() }
      render(<GuidanceSidebar isFacilitator={true} quickActions={quickActions} />)
      expect(screen.getByTestId('quick-action-advance-step')).toHaveTextContent('Advance step →')
    })

    it('calls onAdvanceStep when advance step button is clicked', async () => {
      const { default: userEvent } = await import('@testing-library/user-event')
      const onAdvanceStep = vi.fn()
      render(<GuidanceSidebar isFacilitator={true} quickActions={{ onAdvanceStep }} />)
      await userEvent.setup().click(screen.getByTestId('quick-action-advance-step'))
      expect(onAdvanceStep).toHaveBeenCalledOnce()
    })

    it('quick actions section has data-assistant-testid="facilitator-quick-actions"', () => {
      const quickActions: FacilitatorQuickActionsConfig = { onAdvanceStep: vi.fn() }
      render(<GuidanceSidebar isFacilitator={true} quickActions={quickActions} />)
      expect(screen.getByTestId('facilitator-quick-actions')).toHaveAttribute('data-assistant-testid', 'facilitator-quick-actions')
    })

    it('shows placeholder instead of section when quick actions absent', () => {
      render(<GuidanceSidebar isFacilitator={false} />)
      expect(screen.queryByTestId('facilitator-quick-actions')).not.toBeInTheDocument()
      expect(screen.getByTestId('facilitator-quick-actions-placeholder')).toBeInTheDocument()
    })

    it('placeholder has data-assistant-testid="facilitator-quick-actions"', () => {
      render(<GuidanceSidebar isFacilitator={false} />)
      expect(screen.getByTestId('facilitator-quick-actions-placeholder')).toHaveAttribute('data-assistant-testid', 'facilitator-quick-actions')
    })
  })
})
