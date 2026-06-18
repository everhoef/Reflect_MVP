import AppRoutes from '@/app/routes'
import { Toaster } from '@/shared/ui/sonner'

export default function App() {
  return (
    <>
      <AppRoutes />
      <Toaster />
    </>
  )
}
