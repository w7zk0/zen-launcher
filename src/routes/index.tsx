import { createFileRoute } from "@tanstack/react-router";
import { ZenScreen } from "@/components/zen/ZenScreen";

export const Route = createFileRoute("/")({ component: Home });

function Home() {
  return <ZenScreen />;
}
