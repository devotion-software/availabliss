<script lang="ts">
	import { fileIconKind, type FileIconKind } from '#lib/client/attachments';
	import Icon, { type IconName } from '#lib/components/ui/Icon.svelte';

	/** A file's icon by its type: document, PDF, spreadsheet, slides, ZIP, audio, video or plain file. */
	interface Props {
		name: string;
		mime?: string;
		size?: number;
	}
	let { name, mime = '', size = 22 }: Props = $props();

	const ICON: Record<FileIconKind, IconName> = {
		pdf: 'file-text',
		doc: 'file-text',
		sheet: 'file-sheet',
		slides: 'file-slides',
		zip: 'file-zip',
		audio: 'music',
		video: 'video',
		image: 'image',
		file: 'file'
	};
</script>

<Icon name={ICON[fileIconKind(name, mime)]} {size} />
