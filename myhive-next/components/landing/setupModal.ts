// TripSetupModal is legacy JS whose vote-mode callback props default to null,
// so tsc infers their type as `null` and rejects real handlers. Re-typed once
// here for both landings' vote-mode mounts.
import type { ComponentType } from 'react';
import LegacyTripSetupModal from '../../legacy-src/components/TripSetupModal';

export interface VoteSetup {
  travelers: number;
  startDate: string;
  endDate: string;
  destination: { id: string; slug: string; name: string };
  budget: number | null;
}

const TripSetupModal = LegacyTripSetupModal as ComponentType<{
  isVoteMode?: boolean;
  voteOpen?: boolean;
  onVoteConfirm?: (setup: VoteSetup) => void;
  onVoteCancel?: () => void;
  preselectedDestination?: { id: string; slug: string; name: string } | null;
}>;

export default TripSetupModal;
