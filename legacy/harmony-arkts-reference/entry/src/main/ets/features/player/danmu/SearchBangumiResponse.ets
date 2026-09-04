export interface SearchBangumiResponse {
	errorCode: number;
	success: boolean;
	errorMessage: string;
	hasMore: boolean;
	animes: SearchEpisodesAnime[];
}
export interface SearchEpisodeDetails {
	episodeId: number;
	episodeTitle: string;
}
export interface SearchEpisodesAnime {
	animeId: number;
	animeTitle: string;
	type: string;
	typeDescription: string;
	episodes: SearchEpisodeDetails[];
}