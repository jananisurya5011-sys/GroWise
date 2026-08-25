export const getDiagnosisImageUrl = (filename) => {
  if (!filename) return null;
  // Clean the path just in case the backend stored it with slashes
  const cleanFilename = filename.split(/[/\\]/).pop();
  return `/api/crop-doctor/serve-image/${cleanFilename}`;
};
