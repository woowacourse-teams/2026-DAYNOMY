import { useState } from 'react';

export function InvestorAvatar({ name, imageUrl }: { name: string; imageUrl?: string | null }) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  return (
    <span className="league-avatar" aria-hidden="true">
      {imageUrl && imageUrl !== failedUrl ? (
        <img
          src={imageUrl}
          alt=""
          loading="lazy"
          referrerPolicy="no-referrer"
          onError={() => setFailedUrl(imageUrl)}
        />
      ) : (
        name.slice(0, 1)
      )}
    </span>
  );
}
