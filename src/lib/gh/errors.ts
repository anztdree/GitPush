/** Error thrown by GitHub API calls with a friendly Indonesian message. */
export class GHError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = 'GHError';
    this.status = status;
  }
}
