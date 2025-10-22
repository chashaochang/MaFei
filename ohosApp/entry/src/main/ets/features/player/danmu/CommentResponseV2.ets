export interface CommentResponseV2 {
	count: number;
	comments: CommentData[];
}
export interface CommentData {
	cid: number;
	p: string;
	m: string;
}