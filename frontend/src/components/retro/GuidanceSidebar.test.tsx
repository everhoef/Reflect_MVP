import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { GuidanceSidebar, type HistoryMessage, type FacilitatorQuickActionsConfig } from './GuidanceSidebar'

describe('GuidanceSidebar', () => {
  it('renders the default label when no stepTitle provided', () => {
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

  it('is expanded by default', () => {
    render(<GuidanceSidebar guidance="Some guidance text" />)
    expect(screen.getByText('Some guidance text')).toBeVisible()
  })

  it('collapses when the toggle button is clicked', async () => {
    const user = userEvent.setup()
    render(<GuidanceSidebar guidance="Some guidance text" />)

    const toggleButton = screen.getByRole('button', { name: 'Collapse guidance' })
    await user.click(toggleButton)

    expect(screen.queryByText('Some guidance text')).not.toBeInTheDocument()
  })

  it('shows toggle text "↓ Show" when collapsed', async () => {
    const user = userEvent.setup()
    render(<GuidanceSidebar guidance="Some guidance text" />)

    await user.click(screen.getByRole('button', { name: 'Collapse guidance' }))

    expect(screen.getByText('↓ Show')).toBeInTheDocument()
  })

  it('expands again when the expand toggle is clicked', async () => {
    const user = userEvent.setup()
    render(<GuidanceSidebar guidance="Some guidance text" />)

    await user.click(screen.getByRole('button', { name: 'Collapse guidance' }))
    expect(screen.queryByText('Some guidance text')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Expand guidance' }))
    expect(screen.getByText('Some guidance text')).toBeInTheDocument()
  })

  it('toggle button has correct aria-expanded attribute', async () => {
    const user = userEvent.setup()
    render(<GuidanceSidebar />)

    const toggleButton = screen.getByRole('button', { name: 'Collapse guidance' })
    expect(toggleButton).toHaveAttribute('aria-expanded', 'true')

    await user.click(toggleButton)
    expect(screen.getByRole('button', { name: 'Expand guidance' })).toHaveAttribute('aria-expanded', 'false')
  })

  it('Card root has data-coachmark="guidance-sidebar" anchor attribute', () => {
    render(<GuidanceSidebar guidance="Some guidance text" />)
    const card = document.querySelector('[data-coachmark="guidance-sidebar"]')
    expect(card).toBeInTheDocument()
  })

  describe('stable selector hook contract', () => {
    it('renders data-testid="guidance-sidebar" on the shell root', () => {
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('guidance-sidebar')).toBeInTheDocument()
    })

    it('renders data-testid="guidance-content" when expanded (default)', () => {
      render(<GuidanceSidebar guidance="Some guidance" />)
      expect(screen.getByTestId('guidance-content')).toBeInTheDocument()
    })

    it('guidance-content is absent when collapsed', async () => {
      const user = userEvent.setup()
      render(<GuidanceSidebar guidance="Some guidance" />)
      await user.click(screen.getByRole('button', { name: 'Collapse guidance' }))
      expect(screen.queryByTestId('guidance-content')).not.toBeInTheDocument()
    })

    it('shell root carries data-assistant-testid="assistant-shell"', () => {
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('guidance-sidebar')).toHaveAttribute('data-assistant-testid', 'assistant-shell')
    })

    it('guidance-content carries data-assistant-testid="assistant-current-message"', () => {
      render(<GuidanceSidebar guidance="Some guidance" />)
      expect(screen.getByTestId('guidance-content')).toHaveAttribute('data-assistant-testid', 'assistant-current-message')
    })

    it('assistant-history-list placeholder is always in DOM regardless of expand state', async () => {
      const user = userEvent.setup()
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('assistant-history-list')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Collapse guidance' }))
      expect(screen.getByTestId('assistant-history-list')).toBeInTheDocument()
    })

    it('assistant-history-list placeholder is hidden', () => {
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('assistant-history-list')).toHaveAttribute('aria-hidden', 'true')
    })

    it('assistant-private-coaching-placeholder is always in DOM', () => {
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('assistant-private-coaching-placeholder')).toBeInTheDocument()
    })

    it('assistant-private-coaching-placeholder is hidden', () => {
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('assistant-private-coaching-placeholder')).toHaveAttribute('aria-hidden', 'true')
    })

    it('assistant-history-list carries data-assistant-testid="assistant-history-list"', () => {
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('assistant-history-list')).toHaveAttribute('data-assistant-testid', 'assistant-history-list')
    })

    it('assistant-private-coaching-placeholder carries data-assistant-testid="assistant-private-coaching"', () => {
      render(<GuidanceSidebar />)
      expect(screen.getByTestId('assistant-private-coaching-placeholder')).toHaveAttribute('data-assistant-testid', 'assistant-private-coaching')
    })
  })

  describe('facilitator-private coaching boundary', () => {
    it('facilitator with a note sees assistant-private-coaching section', () => {
      render(
        <GuidanceSidebar
          isFacilitator={true}
          privateCoachingNote={{ text: 'Click Next when ready.' }}
        />
      )
      expect(screen.getByTestId('assistant-private-coaching')).toBeInTheDocument()
      expect(screen.queryByTestId('assistant-private-coaching-placeholder')).not.toBeInTheDocument()
    })

    it('private coaching section shows the note text', () => {
      render(
        <GuidanceSidebar
          isFacilitator={true}
          privateCoachingNote={{ text: 'Click Next when ready.' }}
        />
      )
      expect(screen.getByText('Click Next when ready.')).toBeInTheDocument()
    })

    it('private coaching section shows "Private note" label', () => {
      render(
        <GuidanceSidebar
          isFacilitator={true}
          privateCoachingNote={{ text: 'Click Next when ready.' }}
        />
      )
      expect(screen.getByText(/Private note/i)).toBeInTheDocument()
    })

    it('non-facilitator sees only the placeholder, not the coaching section', () => {
      render(
        <GuidanceSidebar
          isFacilitator={false}
          privateCoachingNote={{ text: 'Click Next when ready.' }}
        />
      )
      expect(screen.queryByTestId('assistant-private-coaching')).not.toBeInTheDocument()
      expect(screen.getByTestId('assistant-private-coaching-placeholder')).toBeInTheDocument()
    })

    it('omitting isFacilitator shows only the placeholder', () => {
      render(<GuidanceSidebar privateCoachingNote={{ text: 'Click Next when ready.' }} />)
      expect(screen.queryByTestId('assistant-private-coaching')).not.toBeInTheDocument()
      expect(screen.getByTestId('assistant-private-coaching-placeholder')).toBeInTheDocument()
    })

    it('no coaching note shows only the placeholder even for facilitator', () => {
      render(<GuidanceSidebar isFacilitator={true} privateCoachingNote={null} />)
      expect(screen.queryByTestId('assistant-private-coaching')).not.toBeInTheDocument()
      expect(screen.getByTestId('assistant-private-coaching-placeholder')).toBeInTheDocument()
    })

    it('guidance-content subtree does not contain private coaching text', () => {
      render(
        <GuidanceSidebar
          guidance="Shared guidance here."
          isFacilitator={true}
          privateCoachingNote={{ text: 'SECRET facilitator note.' }}
        />
      )
      const guidanceContent = screen.getByTestId('guidance-content')
      expect(guidanceContent).not.toHaveTextContent('SECRET facilitator note.')
    })

    it('private coaching section is structurally outside guidance-content', () => {
      render(
        <GuidanceSidebar
          guidance="Shared guidance here."
          isFacilitator={true}
          privateCoachingNote={{ text: 'SECRET facilitator note.' }}
        />
      )
      const guidanceContent = screen.getByTestId('guidance-content')
      const privateSection = screen.getByTestId('assistant-private-coaching')
      expect(guidanceContent.contains(privateSection)).toBe(false)
    })
  })

  describe('shared assistant history rendering', () => {
    const makeHistory = (n: number): HistoryMessage[] =>
      Array.from({ length: n }, (_, i) => ({
        stepTitle: `Step ${n - i}`,
        publicText: `Message ${n - i}`,
      }))

    it('renders no history items when previousMessages is empty', () => {
      render(<GuidanceSidebar guidance="Current" previousMessages={[]} />)
      const historyList = screen.getByTestId('assistant-history-list')
      expect(historyList.children).toHaveLength(0)
    })

    it('renders 1 previous message when provided', () => {
      const history = makeHistory(1)
      render(<GuidanceSidebar guidance="Current" previousMessages={history} />)
      const historyList = screen.getByTestId('assistant-history-list')
      // Count only item <div>s — the "Earlier" <p> header is also a direct child
      expect(historyList.querySelectorAll(':scope > div')).toHaveLength(1)
      expect(within(historyList).getByText('Message 1')).toBeInTheDocument()
    })

    it('renders up to 3 previous messages when 3 are provided', () => {
      const history = makeHistory(3)
      render(<GuidanceSidebar guidance="Current" previousMessages={history} />)
      const historyList = screen.getByTestId('assistant-history-list')
      // Count only item <div>s — the "Earlier" <p> header is also a direct child
      expect(historyList.querySelectorAll(':scope > div')).toHaveLength(3)
      expect(within(historyList).getByText('Message 3')).toBeInTheDocument()
      expect(within(historyList).getByText('Message 2')).toBeInTheDocument()
      expect(within(historyList).getByText('Message 1')).toBeInTheDocument()
    })

    it('renders only 3 items when 4 previous messages are provided (cap enforced)', () => {
      const history = makeHistory(4)
      render(<GuidanceSidebar guidance="Current" previousMessages={history} />)
      const historyList = screen.getByTestId('assistant-history-list')
      // Count only item <div>s — the "Earlier" <p> header is also a direct child
      expect(historyList.querySelectorAll(':scope > div')).toHaveLength(3)
      expect(within(historyList).queryByText('Message 1')).not.toBeInTheDocument()
    })

    it('renders only 3 items when 6 previous messages are provided (cap enforced)', () => {
      const history = makeHistory(6)
      render(<GuidanceSidebar guidance="Current" previousMessages={history} />)
      const historyList = screen.getByTestId('assistant-history-list')
      // Count only item <div>s — the "Earlier" <p> header is also a direct child
      expect(historyList.querySelectorAll(':scope > div')).toHaveLength(3)
    })

    it('current message remains primary (guidance-content is present and rendered above history)', () => {
      const history = makeHistory(3)
      render(<GuidanceSidebar guidance="Current guidance" previousMessages={history} />)
      expect(screen.getByTestId('guidance-content')).toBeInTheDocument()
      expect(screen.getByText('Current guidance')).toBeInTheDocument()
    })

    it('history items do not appear in the guidance-content subtree', () => {
      const history = makeHistory(2)
      render(<GuidanceSidebar guidance="Current guidance" previousMessages={history} />)
      const guidanceContent = screen.getByTestId('guidance-content')
      expect(guidanceContent).not.toHaveTextContent('Message 2')
      expect(guidanceContent).not.toHaveTextContent('Message 1')
    })

    it('history container is always in DOM regardless of expand state', async () => {
      const user = userEvent.setup()
      const history = makeHistory(2)
      render(<GuidanceSidebar guidance="Current" previousMessages={history} />)
      expect(screen.getByTestId('assistant-history-list')).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'Collapse guidance' }))
      expect(screen.getByTestId('assistant-history-list')).toBeInTheDocument()
    })

    it('history list is not aria-hidden when history items exist', () => {
      const history = makeHistory(2)
      render(<GuidanceSidebar guidance="Current" previousMessages={history} />)
      const historyList = screen.getByTestId('assistant-history-list')
      expect(historyList).not.toHaveAttribute('aria-hidden', 'true')
    })

    it('history list is aria-hidden when no history is provided', () => {
      render(<GuidanceSidebar guidance="Current" />)
      const historyList = screen.getByTestId('assistant-history-list')
      expect(historyList).toHaveAttribute('aria-hidden', 'true')
    })
  })

  describe('facilitator quick actions', () => {
    it('renders facilitator-quick-actions-placeholder when no quickActions provided', () => {
      render(<GuidanceSidebar isFacilitator={true} />)
      expect(screen.getByTestId('facilitator-quick-actions-placeholder')).toBeInTheDocument()
      expect(screen.queryByTestId('facilitator-quick-actions')).not.toBeInTheDocument()
    })

    it('renders facilitator-quick-actions-placeholder for non-facilitator even with quickActions', () => {
      const onAdvanceStep = vi.fn()
      render(<GuidanceSidebar isFacilitator={false} quickActions={{ onAdvanceStep }} />)
      expect(screen.getByTestId('facilitator-quick-actions-placeholder')).toBeInTheDocument()
      expect(screen.queryByTestId('facilitator-quick-actions')).not.toBeInTheDocument()
    })

    it('renders facilitator-quick-actions section for facilitator with onAdvanceStep', () => {
      const onAdvanceStep = vi.fn()
      render(<GuidanceSidebar isFacilitator={true} quickActions={{ onAdvanceStep }} />)
      expect(screen.getByTestId('facilitator-quick-actions')).toBeInTheDocument()
      expect(screen.queryByTestId('facilitator-quick-actions-placeholder')).not.toBeInTheDocument()
    })

    it('renders "Quick actions" label when actions are available', () => {
      const onAdvanceStep = vi.fn()
      render(<GuidanceSidebar isFacilitator={true} quickActions={{ onAdvanceStep }} />)
      expect(screen.getByText(/Quick actions/i)).toBeInTheDocument()
    })

    it('renders "Advance step →" button when onAdvanceStep is provided', () => {
      const onAdvanceStep = vi.fn()
      render(<GuidanceSidebar isFacilitator={true} quickActions={{ onAdvanceStep }} />)
      expect(screen.getByTestId('quick-action-advance-step')).toBeInTheDocument()
      expect(screen.getByText('Advance step →')).toBeInTheDocument()
    })

    it('calls onAdvanceStep when "Advance step →" is clicked', async () => {
      const user = userEvent.setup()
      const onAdvanceStep = vi.fn()
      render(<GuidanceSidebar isFacilitator={true} quickActions={{ onAdvanceStep }} />)
      await user.click(screen.getByTestId('quick-action-advance-step'))
      expect(onAdvanceStep).toHaveBeenCalledTimes(1)
    })

    it('does not auto-advance: callback is invoked only on user click, not on render', () => {
      const onAdvanceStep = vi.fn()
      render(<GuidanceSidebar isFacilitator={true} quickActions={{ onAdvanceStep }} />)
      expect(onAdvanceStep).not.toHaveBeenCalled()
    })

    it('quick actions section is structurally outside guidance-content', () => {
      const onAdvanceStep = vi.fn()
      render(<GuidanceSidebar guidance="Shared guidance" isFacilitator={true} quickActions={{ onAdvanceStep }} />)
      const guidanceContent = screen.getByTestId('guidance-content')
      const quickActionsSection = screen.getByTestId('facilitator-quick-actions')
      expect(guidanceContent.contains(quickActionsSection)).toBe(false)
    })

    it('placeholder carries data-assistant-testid="facilitator-quick-actions"', () => {
      render(<GuidanceSidebar isFacilitator={true} />)
      expect(screen.getByTestId('facilitator-quick-actions-placeholder')).toHaveAttribute('data-assistant-testid', 'facilitator-quick-actions')
    })

    it('active section carries data-assistant-testid="facilitator-quick-actions"', () => {
      const onAdvanceStep = vi.fn()
      render(<GuidanceSidebar isFacilitator={true} quickActions={{ onAdvanceStep }} />)
      expect(screen.getByTestId('facilitator-quick-actions')).toHaveAttribute('data-assistant-testid', 'facilitator-quick-actions')
    })

    it('renders placeholder when quickActions is null', () => {
      render(<GuidanceSidebar isFacilitator={true} quickActions={null} />)
      expect(screen.getByTestId('facilitator-quick-actions-placeholder')).toBeInTheDocument()
    })

    it('renders placeholder when quickActions has no onAdvanceStep (empty config)', () => {
      const emptyConfig: FacilitatorQuickActionsConfig = {}
      render(<GuidanceSidebar isFacilitator={true} quickActions={emptyConfig} />)
      expect(screen.getByTestId('facilitator-quick-actions-placeholder')).toBeInTheDocument()
      expect(screen.queryByTestId('facilitator-quick-actions')).not.toBeInTheDocument()
    })
  })
})
