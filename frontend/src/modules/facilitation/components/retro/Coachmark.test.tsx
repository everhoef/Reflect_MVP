import { render, screen, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Coachmark } from '@/modules/facilitation/components/retro/Coachmark'

function mountAnchor(anchorId: string) {
  const el = document.createElement('div')
  el.setAttribute('data-coachmark', anchorId)
  el.style.cssText = 'position:fixed;top:100px;left:200px;width:50px;height:30px'
  document.body.appendChild(el)
  return el
}

function cleanupAnchors() {
  document
    .querySelectorAll('[data-coachmark]')
    .forEach((el) => el.remove())
}

describe('Coachmark primitive', () => {
  afterEach(() => {
    cleanupAnchors()
  })

  describe('anchor lookup and rendering', () => {
    it('renders when anchor is present in the DOM', () => {
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={() => undefined}>
          Welcome to the guidance panel
        </Coachmark>
      )
      expect(screen.getByTestId('coachmark')).toBeInTheDocument()
    })

    it('renders with data-coachmark-popup attribute matching anchorId', () => {
      mountAnchor('next-step')
      render(
        <Coachmark anchorId="next-step" onDismiss={() => undefined}>
          Click Next to advance
        </Coachmark>
      )
      expect(screen.getByTestId('coachmark')).toHaveAttribute('data-coachmark-popup', 'next-step')
    })

    it('renders content in the content container', () => {
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={() => undefined}>
          This is the coachmark message
        </Coachmark>
      )
      expect(screen.getByTestId('coachmark-content')).toHaveTextContent('This is the coachmark message')
    })

    it('renders default label "Tip" when no label prop provided', () => {
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={() => undefined}>
          A tip
        </Coachmark>
      )
      expect(screen.getByText('Tip')).toBeInTheDocument()
    })

    it('renders custom label when provided', () => {
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" label="Hint" onDismiss={() => undefined}>
          A hint
        </Coachmark>
      )
      expect(screen.getByText('Hint')).toBeInTheDocument()
    })

    it('renders nothing when anchor is absent (no orphaned overlay)', () => {
      render(
        <Coachmark anchorId="note-input" onDismiss={() => undefined}>
          Input is available
        </Coachmark>
      )
      expect(screen.queryByTestId('coachmark')).not.toBeInTheDocument()
    })

    it('accepts a custom data-testid', () => {
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark
          anchorId="guidance-sidebar"
          onDismiss={() => undefined}
          data-testid="my-coachmark"
        >
          Custom id
        </Coachmark>
      )
      expect(screen.getByTestId('my-coachmark')).toBeInTheDocument()
      expect(screen.getByTestId('my-coachmark-close')).toBeInTheDocument()
      expect(screen.getByTestId('my-coachmark-content')).toBeInTheDocument()
    })
  })

  describe('dismiss behavior', () => {
    it('is visible initially', () => {
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={() => undefined}>
          Hello
        </Coachmark>
      )
      expect(screen.getByTestId('coachmark')).toBeInTheDocument()
    })

    it('is removed from DOM after close button click', async () => {
      const user = userEvent.setup()
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={() => undefined}>
          Hello
        </Coachmark>
      )
      await user.click(screen.getByRole('button', { name: 'Dismiss coachmark' }))
      expect(screen.queryByTestId('coachmark')).not.toBeInTheDocument()
    })

    it('calls onDismiss callback after close button click', async () => {
      const user = userEvent.setup()
      const onDismiss = vi.fn()
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={onDismiss}>
          Hello
        </Coachmark>
      )
      await user.click(screen.getByRole('button', { name: 'Dismiss coachmark' }))
      expect(onDismiss).toHaveBeenCalledTimes(1)
    })

    it('is removed from DOM after Escape key press', async () => {
      const user = userEvent.setup()
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={() => undefined}>
          Hello
        </Coachmark>
      )
      await user.keyboard('{Escape}')
      expect(screen.queryByTestId('coachmark')).not.toBeInTheDocument()
    })

    it('calls onDismiss callback after Escape key press', async () => {
      const user = userEvent.setup()
      const onDismiss = vi.fn()
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={onDismiss}>
          Hello
        </Coachmark>
      )
      await user.keyboard('{Escape}')
      expect(onDismiss).toHaveBeenCalledTimes(1)
    })

    it('close button has accessible aria-label', () => {
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={() => undefined}>
          Hello
        </Coachmark>
      )
      expect(screen.getByRole('button', { name: 'Dismiss coachmark' })).toBeInTheDocument()
    })
  })

  describe('non-blocking interaction intent', () => {
    it('outer container has pointer-events-none class (does not capture clicks)', () => {
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={() => undefined}>
          Hello
        </Coachmark>
      )
      const container = screen.getByTestId('coachmark')
      expect(container.className).toContain('pointer-events-none')
    })

    it('inner bubble has pointer-events-auto (close button is reachable)', () => {
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={() => undefined}>
          Hello
        </Coachmark>
      )
      const container = screen.getByTestId('coachmark')
      const inner = container.firstElementChild
      expect(inner?.className).toContain('pointer-events-auto')
    })

    it('has role="note" (not role="dialog" — non-modal)', () => {
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={() => undefined}>
          Hello
        </Coachmark>
      )
      expect(screen.getByRole('note')).toBeInTheDocument()
    })

    it('does not trap focus — close button is the only interactive element', () => {
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={() => undefined}>
          Hello
        </Coachmark>
      )
      const interactiveElements = screen
        .getByTestId('coachmark')
        .querySelectorAll('button, a, input, select, textarea, [tabindex]')
      expect(interactiveElements).toHaveLength(1)
      expect(interactiveElements.item(0)).toHaveAttribute('aria-label', 'Dismiss coachmark')
    })
  })

  describe('missing-anchor fallback (Task 13)', () => {
    it('renders nothing when anchor element is not in the DOM', () => {
      render(
        <Coachmark anchorId="note-input" onDismiss={() => undefined}>
          Should not appear
        </Coachmark>
      )
      expect(screen.queryByTestId('coachmark')).not.toBeInTheDocument()
    })

    it('renders nothing when anchor disappears after initial mount', async () => {
      const anchor = mountAnchor('note-input')
      render(
        <Coachmark anchorId="note-input" onDismiss={() => undefined}>
          Was here
        </Coachmark>
      )
      expect(screen.getByTestId('coachmark')).toBeInTheDocument()

      anchor.remove()
      await act(async () => {
        window.dispatchEvent(new Event('resize'))
      })

      expect(screen.queryByTestId('coachmark')).not.toBeInTheDocument()
    })

    it('does not call onDismiss when absent anchor prevents rendering', () => {
      const onDismiss = vi.fn()
      render(
        <Coachmark anchorId="note-input" onDismiss={onDismiss}>
          Should not appear
        </Coachmark>
      )
      expect(onDismiss).not.toHaveBeenCalled()
    })

    it('renders normally once anchor becomes available after re-mount', () => {
      const { unmount } = render(
        <Coachmark anchorId="note-input" onDismiss={() => undefined}>
          Hello
        </Coachmark>
      )
      expect(screen.queryByTestId('coachmark')).not.toBeInTheDocument()
      unmount()

      mountAnchor('note-input')
      render(
        <Coachmark anchorId="note-input" onDismiss={() => undefined}>
          Hello
        </Coachmark>
      )
      expect(screen.getByTestId('coachmark')).toBeInTheDocument()
    })
  })

  describe('position fixed when anchor present', () => {
    it('uses fixed positioning style when anchor is in DOM', () => {
      mountAnchor('guidance-sidebar')
      render(
        <Coachmark anchorId="guidance-sidebar" onDismiss={() => undefined}>
          Hello
        </Coachmark>
      )
      const container = screen.getByTestId('coachmark')
      expect(container.style.position).toBe('fixed')
    })

    it('renders nothing when anchor is absent (no fixed-position orphan)', () => {
      render(
        <Coachmark anchorId="note-input" onDismiss={() => undefined}>
          Hello
        </Coachmark>
      )
      expect(screen.queryByTestId('coachmark')).not.toBeInTheDocument()
    })
  })
})
